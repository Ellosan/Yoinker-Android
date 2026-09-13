package com.pylo.yoinker.core

private val URL_RE = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

/**
 * Shared text is rarely just a link — it usually arrives as
 * "Watch this! https://… " with a title, an emoji and a tracking tail.
 * Pull the first real URL out of whatever we were handed.
 */
fun extractUrl(text: CharSequence?): String? {
    if (text.isNullOrBlank()) return null
    val match = URL_RE.find(text) ?: return null
    // Trailing punctuation from prose ("… see https://x.com/y.") isn't part of the link.
    return match.value.trimEnd('.', ',', ')', ']', '}', '!', '?', ';', ':', '"', '\'')
        .takeIf { it.length > "https://".length }
}

fun isYoinkable(text: CharSequence?): Boolean = extractUrl(text) != null

/** "youtube.com", "soundcloud.com" — just for showing where something came from. */
fun hostOf(url: String): String =
    runCatching { java.net.URI(url).host.orEmpty().removePrefix("www.") }.getOrDefault("")

fun formatDuration(seconds: Int): String {
    if (seconds <= 0) return ""
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return ""
    val units = listOf("B", "KB", "MB", "GB")
    var value = bytes.toDouble()
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (unit == 0) "$bytes B" else "%.1f %s".format(value, units[unit])
}

fun formatEta(seconds: Long): String = when {
    seconds <= 0 -> ""
    seconds < 60 -> "${seconds}s left"
    else -> "${seconds / 60}m ${seconds % 60}s left"
}
