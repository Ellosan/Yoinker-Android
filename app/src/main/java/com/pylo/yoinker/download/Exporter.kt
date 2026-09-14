package com.pylo.yoinker.download

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.pylo.yoinker.core.Fmt
import java.io.File

/**
 * Moves a finished file out of Yoinker's scratch directory and into the phone's
 * media library, so it shows up in the music player or the gallery like anything
 * else — not buried in app storage where only Yoinker can see it.
 */
object Exporter {

    private const val FOLDER = "Yoinker"

    data class Saved(val uri: Uri?, val displayName: String, val sizeBytes: Long)

    fun export(context: Context, file: File, fmt: Fmt): Saved {
        val name = file.name
        val mime = if (fmt.isAudio) "audio/mpeg" else "video/mp4"
        val size = file.length()

        val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            insertViaMediaStore(context, file, name, mime, fmt)
        } else {
            copyToPublicDir(context, file, name, fmt)
        }

        if (uri != null) file.delete()
        return Saved(uri, name, size)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun insertViaMediaStore(context: Context, file: File, name: String, mime: String, fmt: Fmt): Uri? {
        val collection = if (fmt.isAudio) {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val baseDir = if (fmt.isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, "$baseDir/$FOLDER")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(collection, values) ?: return null
        return try {
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IllegalStateException("Couldn't open $name for writing.")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            uri
        } catch (e: Exception) {
            // Half-written entries would show up as broken files in the gallery.
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
    }

    private fun copyToPublicDir(context: Context, file: File, name: String, fmt: Fmt): Uri? {
        val baseDir = if (fmt.isAudio) Environment.DIRECTORY_MUSIC else Environment.DIRECTORY_MOVIES
        val dir = publicDir(context, baseDir)
        dir.mkdirs()
        val target = dedupe(dir, name)
        file.inputStream().use { input -> target.outputStream().use { input.copyTo(it) } }
        // Pre-Q there's no MediaStore insert; the scanner is what makes it visible.
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
        return Uri.fromFile(target)
    }

    /**
     * Before scoped storage, writing to Music/ needs a permission the user can refuse.
     * If it isn't granted, the file still gets saved — in Yoinker's own folder on the
     * shared card, which needs no permission at all — rather than being thrown away.
     */
    private fun publicDir(context: Context, baseDir: String): File {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) return File(Environment.getExternalStoragePublicDirectory(baseDir), FOLDER)
        return File(context.getExternalFilesDir(baseDir) ?: context.filesDir, FOLDER)
    }

    /**
     * MediaStore renames clashing entries itself on Q and up; the pre-Q path has to
     * do it by hand, or yoinking the same song twice silently overwrites the first.
     */
    private fun dedupe(dir: File, name: String): File {
        val target = File(dir, name)
        if (!target.exists()) return target
        val stem = name.substringBeforeLast('.', name)
        val ext = name.substringAfterLast('.', "")
        var n = 1
        while (true) {
            val candidate = File(dir, if (ext.isEmpty()) "$stem ($n)" else "$stem ($n).$ext")
            if (!candidate.exists()) return candidate
            n++
        }
    }

    fun mimeFor(fmt: Fmt): String = if (fmt.isAudio) "audio/mpeg" else "video/mp4"
}
