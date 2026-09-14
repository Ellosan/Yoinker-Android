package com.pylo.yoinker.convert

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.provider.OpenableColumns

/**
 * What a picked file is, worked out without copying a byte of it.
 *
 * MediaExtractor reads content Uris directly and reports each track's MIME type,
 * which is what says whether the phone will actually draw the video — the whole
 * reason someone opens this screen.
 */
data class SourceInfo(
    val name: String,
    val sizeBytes: Long,
    val durationSec: Double,
    val videoMime: String?,
    val audioMime: String?,
    val width: Int,
    val height: Int,
) {
    val hasVideo: Boolean get() = videoMime != null

    /** "VP9", "H.264" — the short name, for showing someone. */
    val videoCodecName: String?
        get() = when {
            videoMime == null -> null
            videoMime.contains("avc") -> "H.264"
            videoMime.contains("hevc") -> "HEVC"
            videoMime.contains("vp9") -> "VP9"
            videoMime.contains("vp8") -> "VP8"
            videoMime.contains("av01") || videoMime.contains("av1") -> "AV1"
            else -> videoMime.substringAfterLast('/')
        }

    /**
     * True when this phone claims it can decode the video. MediaExtractor reporting
     * a track is not the same as the device being able to play it, but a codec the
     * platform doesn't list is one nothing on the phone will draw.
     */
    val looksPlayable: Boolean
        get() = videoMime == null || videoMime.contains("avc") || videoMime.contains("hevc")

    companion object {

        fun read(context: Context, uri: Uri): SourceInfo? {
            val (name, size) = describe(context, uri)

            var videoMime: String? = null
            var audioMime: String? = null
            var width = 0
            var height = 0
            var durationUs = 0L

            runCatching {
                val extractor = MediaExtractor()
                try {
                    extractor.setDataSource(context, uri, null)
                    for (i in 0 until extractor.trackCount) {
                        val format = extractor.getTrackFormat(i)
                        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                        if (format.containsKey(MediaFormat.KEY_DURATION)) {
                            durationUs = maxOf(durationUs, format.getLong(MediaFormat.KEY_DURATION))
                        }
                        when {
                            mime.startsWith("video/") && videoMime == null -> {
                                videoMime = mime
                                width = format.optInt(MediaFormat.KEY_WIDTH)
                                height = format.optInt(MediaFormat.KEY_HEIGHT)
                            }
                            mime.startsWith("audio/") && audioMime == null -> audioMime = mime
                        }
                    }
                } finally {
                    extractor.release()
                }
            }

            if (name.isEmpty() && videoMime == null && audioMime == null) return null

            return SourceInfo(
                name = name.ifEmpty { "file" },
                sizeBytes = size,
                durationSec = durationUs / 1_000_000.0,
                videoMime = videoMime,
                audioMime = audioMime,
                width = width,
                height = height,
            )
        }

        private fun describe(context: Context, uri: Uri): Pair<String, Long> {
            val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
            val row = runCatching {
                context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        cursor.getString(0).orEmpty() to (if (cursor.isNull(1)) 0L else cursor.getLong(1))
                    } else {
                        null
                    }
                }
            }.getOrNull()
            return row ?: ((uri.lastPathSegment?.substringAfterLast('/') ?: "") to 0L)
        }

        private fun MediaFormat.optInt(key: String): Int =
            if (containsKey(key)) getInteger(key) else 0
    }
}
