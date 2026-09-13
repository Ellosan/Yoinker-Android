package com.pylo.yoinker.core

/** What you get out the other end. Same two the desktop build offers. */
enum class Fmt(val id: String, val label: String, val ext: String, val emoji: String) {
    MP3("mp3", "MP3", "mp3", "🎵"),
    MP4("mp4", "MP4", "mp4", "🎬");

    val isAudio: Boolean get() = this == MP3

    companion object {
        fun of(id: String?): Fmt = entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: MP3
    }
}

data class QualityOption(val value: String, val label: String)

object Qualities {
    private val mp3 = listOf(
        QualityOption("320K", "320 kbps — best"),
        QualityOption("192K", "192 kbps — good"),
        QualityOption("128K", "128 kbps — small"),
    )
    private val mp4 = listOf(
        QualityOption("best", "Best available"),
        QualityOption("1080", "1080p"),
        QualityOption("720", "720p"),
        QualityOption("480", "480p — small"),
    )

    fun forFormat(fmt: Fmt): List<QualityOption> = if (fmt.isAudio) mp3 else mp4

    fun default(fmt: Fmt): String = if (fmt.isAudio) "192K" else "best"

    /** Falls back to the default when a saved mode names a quality of the other format. */
    fun sanitize(fmt: Fmt, value: String?): String =
        forFormat(fmt).firstOrNull { it.value == value }?.value ?: default(fmt)

    fun labelOf(fmt: Fmt, value: String): String =
        forFormat(fmt).firstOrNull { it.value == value }?.label ?: value
}
