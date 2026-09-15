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
    return trimProse(match.value).takeIf { it.length > "https://".length }
}

private const val PROSE_TAIL = ".,!?;:\"'"

/**
 * Strips the punctuation that ended the sentence rather than the link.
 *
 * Brackets need care: trimming every trailing one turns
 * `…/wiki/Yoink_(disambiguation)` into a 404, while `(see …/clip)` really does end
 * with the prose's own bracket. The link keeps a closing bracket only when it
 * opened one itself.
 */
private fun trimProse(url: String): String {
    var end = url.length
    while (end > 0) {
        val last = url[end - 1]
        val opener = when (last) {
            ')' -> '('
            ']' -> '['
            '}' -> '{'
            else -> null
        }
        when {
            opener != null -> {
                val kept = url.substring(0, end)
                if (kept.count { it == opener } >= kept.count { it == last }) break
                end--
            }

            last in PROSE_TAIL -> end--
            else -> break
        }
    }
    return url.substring(0, end)
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
