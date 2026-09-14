package com.pylo.yoinker.engine

import android.content.Context
import com.pylo.yoinker.core.Fmt
import com.pylo.yoinker.core.Prefs
import com.yausername.aria2c.Aria2c
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/** What a link turned out to be, before you commit to downloading it. */
data class MediaInfo(
    val title: String,
    val uploader: String?,
    val durationSec: Int,
    val thumbnail: String?,
    val ext: String?,
)

/**
 * The download engine: yt-dlp and ffmpeg, running on the phone itself.
 *
 * The desktop build shells out to yt-dlp.exe; here the same binaries are unpacked
 * out of the APK on first run. The arguments below are deliberately the same ones,
 * so a link that yields a clean MP3 on the PC yields the same file here.
 */
object YoinkEngine {

    sealed interface State {
        data object Idle : State
        data object Preparing : State
        data object Ready : State
        data class Failed(val message: String) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private const val OUT_TEMPLATE = "%(title).200B [%(id)s].%(ext)s"
    private const val FILE_MARKER = "YKFILE:"

    @Volatile
    private var ready = false

    /**
     * Unpacks python, yt-dlp and ffmpeg. Slow on first run, a no-op afterwards.
     * Blocking — never call this from the main thread.
     */
    @Synchronized
    fun ensureInit(context: Context): Boolean {
        if (ready) return true
        _state.value = State.Preparing
        return try {
            val app = context.applicationContext
            YoutubeDL.getInstance().init(app)
            FFmpeg.getInstance().init(app)
            Aria2c.getInstance().init(app)
            ready = true
            _state.value = State.Ready
            true
        } catch (e: YoutubeDLException) {
            _state.value = State.Failed(friendly(e))
            false
        } catch (e: Exception) {
            _state.value = State.Failed(friendly(e))
            false
        }
    }

    fun engineVersion(context: Context): String? =
        runCatching { YoutubeDL.getInstance().versionName(context) ?: YoutubeDL.getInstance().version(context) }
            .getOrNull()

    /** Metadata only — title, thumbnail, duration. No bytes are downloaded. */
    fun peek(context: Context, url: String): Result<MediaInfo> {
        if (!ensureInit(context)) return Result.failure(IllegalStateException(stateMessage()))
        return runCatching {
            val request = YoutubeDLRequest(url)
            request.addOption("--no-playlist")
            request.addOption("--skip-download")
            val info = YoutubeDL.getInstance().getInfo(request)
            MediaInfo(
                title = info.title ?: info.fulltitle ?: "Untitled",
                uploader = info.uploader ?: info.uploaderId ?: info.extractorKey,
                durationSec = info.duration,
                thumbnail = info.thumbnail ?: info.thumbnails?.lastOrNull()?.url,
                ext = info.ext,
            )
        }.recoverCatching { throw IllegalStateException(friendly(it)) }
    }

    /**
     * Downloads into [outDir] and returns the finished file.
     *
     * [processId] is the handle [cancel] needs, so give every job its own.
     */
    fun download(
        context: Context,
        url: String,
        fmt: Fmt,
        quality: String,
        outDir: File,
        processId: String,
        onProgress: (percent: Float, etaSeconds: Long, line: String) -> Unit,
    ): Result<File> {
        if (!ensureInit(context)) return Result.failure(IllegalStateException(stateMessage()))
        outDir.mkdirs()
        var printedPath: String? = null

        return runCatching {
            val request = buildRequest(url, fmt, quality, outDir)
            val response = YoutubeDL.getInstance().execute(request, processId) { percent, eta, line ->
                line.lineSequence()
                    .firstOrNull { it.contains(FILE_MARKER) }
                    ?.let { printedPath = it.substringAfter(FILE_MARKER).trim() }
                onProgress(percent, eta, line)
            }
            if (response.exitCode != 0) {
                throw IllegalStateException(cleanError(response.err) ?: "Download failed (exit ${response.exitCode}).")
            }
            resolveOutput(outDir, printedPath)
                ?: throw IllegalStateException("The download finished but no file turned up.")
        }.recoverCatching { throw IllegalStateException(friendly(it)) }
    }

    fun cancel(processId: String): Boolean =
        runCatching { YoutubeDL.getInstance().destroyProcessById(processId) }.getOrDefault(false)

    /** The one-click fix for "it suddenly stopped working" — sites change, yt-dlp keeps up. */
    fun updateEngine(context: Context): Result<String> {
        if (!ensureInit(context)) return Result.failure(IllegalStateException(stateMessage()))
        return runCatching {
            val channel = when (Prefs.engineChannel) {
                "stable" -> YoutubeDL.UpdateChannel.STABLE
                "master" -> YoutubeDL.UpdateChannel.MASTER
                else -> YoutubeDL.UpdateChannel.NIGHTLY
            }
            val status = YoutubeDL.getInstance().updateYoutubeDL(context, channel)
            Prefs.lastEngineUpdate = System.currentTimeMillis()
            when (status) {
                YoutubeDL.UpdateStatus.DONE -> "Engine updated to ${engineVersion(context) ?: "the latest build"}."
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "Engine already up to date."
                else -> "Engine checked."
            }
        }.recoverCatching { throw IllegalStateException(friendly(it)) }
    }

    /** Quietly freshens the engine at most once a day, like the desktop build does. */
    fun maybeAutoUpdate(context: Context) {
        if (!Prefs.autoUpdateEngine) return
        if (System.currentTimeMillis() - Prefs.lastEngineUpdate < 20 * 3600 * 1000L) return
        Prefs.lastEngineUpdate = System.currentTimeMillis()
        updateEngine(context)
    }

    private fun buildRequest(url: String, fmt: Fmt, quality: String, outDir: File): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
        request.addOption("--no-playlist")
        request.addOption("--newline")
        request.addOption("--no-mtime")
        request.addOption("-o", File(outDir, OUT_TEMPLATE).absolutePath)
        request.addOption("--no-simulate")
        request.addOption("--print", "after_move:$FILE_MARKER%(filepath)s")

        if (fmt.isAudio) {
            request.addOption("-x")
            request.addOption("--audio-format", "mp3")
            request.addOption("--audio-quality", quality)
        } else {
            request.addOption("-f", videoSelector(quality))
            request.addOption("--merge-output-format", "mp4")
        }
        return request
    }

    /**
     * Which stream to take, in order of preference.
     *
     * Asking for `[ext=mp4]` is not the same as asking for something the phone can
     * play: ext is the container, and Instagram (among others) serves VP9 video
     * inside an MP4. Worse, yt-dlp's default sorting prefers VP9 and AV1 over H.264
     * because they compress better — so "best video in an mp4" reliably picked the
     * one codec a stock Android player won't put on screen. The file downloads, the
     * audio plays, the picture never arrives.
     *
     * So ask by codec first. H.264 with AAC audio is the combination every Android
     * device since forever decodes in hardware; the container rules stay on as a
     * fallback for sources that don't report codecs at all.
     */
    private fun videoSelector(quality: String): String {
        val cap = if (quality != "best") "[height<=$quality]" else ""
        return listOf(
            "bv*$cap[vcodec^=avc1]+ba[acodec^=mp4a]",
            "bv*$cap[vcodec^=avc]+ba",
            "bv*$cap[vcodec^=h264]+ba",
            "b$cap[vcodec^=avc]",
            "b$cap[vcodec^=h264]",
            "bv*$cap[ext=mp4]+ba[ext=m4a]",
            "b$cap[ext=mp4]",
            "bv*$cap+ba",
            "b$cap",
        ).joinToString("/")
    }

    /**
     * yt-dlp prints the final path, but a failed `--print` shouldn't lose the file:
     * the job downloads into its own directory, so whatever landed there is ours.
     */
    private fun resolveOutput(outDir: File, printedPath: String?): File? {
        printedPath?.let { path ->
            File(path).takeIf { it.isFile }?.let { return it }
            File(outDir, File(path).name).takeIf { it.isFile }?.let { return it }
        }
        return outDir.listFiles()
            ?.filter { it.isFile && !it.name.endsWith(".part") && !it.name.endsWith(".ytdl") }
            ?.maxByOrNull { it.length() }
    }

    private fun stateMessage(): String =
        (_state.value as? State.Failed)?.message ?: "The download engine isn't ready yet."

    /** yt-dlp's ERROR: noise, trimmed into something a person can act on. */
    private fun cleanError(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val line = raw.lineSequence().lastOrNull { it.contains("ERROR", ignoreCase = true) }
            ?: raw.lineSequence().lastOrNull { it.isNotBlank() }
        return line?.replace(Regex("^ERROR:\\s*", RegexOption.IGNORE_CASE), "")?.trim()?.take(240)
    }

    private fun friendly(t: Throwable): String {
        val raw = t.message.orEmpty()
        val cleaned = cleanError(raw) ?: raw
        return when {
            cleaned.isBlank() -> "Something went wrong with that link."
            cleaned.contains("Unable to download webpage", true) ||
                cleaned.contains("Failed to resolve", true) ||
                cleaned.contains("Network is unreachable", true) -> "Couldn't reach that link — check your connection."
            cleaned.contains("Unsupported URL", true) -> "That site isn't supported."
            cleaned.contains("Private video", true) ||
                cleaned.contains("Sign in", true) -> "That one is private or needs a sign-in."
            cleaned.contains("HTTP Error 403", true) -> "The site refused the download. Try Update engine."
            else -> cleaned.take(240)
        }
    }
}
