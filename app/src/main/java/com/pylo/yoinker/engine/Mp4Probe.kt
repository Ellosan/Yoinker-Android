package com.pylo.yoinker.engine

import java.io.File
import java.io.RandomAccessFile

/**
 * Reads the video codec out of an MP4, without needing ffprobe.
 *
 * This exists because a downloaded file can be perfectly valid and still show
 * nothing: VP9 or AV1 video inside an MP4 container is legal, and stock Android
 * players routinely refuse it. The selector in [YoinkEngine] is what stops that
 * happening; this is the check that makes sure, so a blank-screen file is never
 * handed over as though it were fine.
 */
object Mp4Probe {

    /** Codecs a stock Android player reliably puts on screen from inside an MP4. */
    private val PLAYABLE = setOf("avc1", "avc3", "hvc1", "hev1", "mp4v")

    /** The moov box holds the metadata; anything past a few MB of it isn't ours. */
    private const val MAX_MOOV = 8 * 1024 * 1024

    private data class Box(val type: String, val start: Int, val end: Int)

    /** e.g. "avc1", "vp09", "av01" — or null if the file can't be read as an MP4. */
    fun videoCodec(file: File): String? {
        val moov = runCatching { readMoov(file) }.getOrNull() ?: return null

        for (trak in boxes(moov, 0, moov.size).filter { it.type == "trak" }) {
            val mdia = boxes(moov, trak.start, trak.end).firstOrNull { it.type == "mdia" } ?: continue
            val inMdia = boxes(moov, mdia.start, mdia.end)

            // hdlr payload: version+flags, pre_defined, then the handler type.
            val hdlr = inMdia.firstOrNull { it.type == "hdlr" } ?: continue
            if (hdlr.end - hdlr.start < 12) continue
            if (fourCC(moov, hdlr.start + 8) != "vide") continue

            val minf = inMdia.firstOrNull { it.type == "minf" } ?: continue
            val stbl = boxes(moov, minf.start, minf.end).firstOrNull { it.type == "stbl" } ?: continue
            val stsd = boxes(moov, stbl.start, stbl.end).firstOrNull { it.type == "stsd" } ?: continue

            // stsd payload: version+flags, entry count, then [size][format] per entry.
            if (stsd.end - stsd.start < 16) continue
            return fourCC(moov, stsd.start + 12)
        }
        return null
    }

    /** Null codec means "couldn't tell" — no reason to worry anyone over that. */
    fun isStockPlayable(codec: String?): Boolean = codec == null || codec in PLAYABLE

    /** A plain-English name for the codecs people actually run into. */
    fun codecName(codec: String?): String = when (codec) {
        "vp09", "vp08" -> "VP9"
        "av01" -> "AV1"
        null -> "an unknown codec"
        else -> codec
    }

    private fun readMoov(file: File): ByteArray? {
        RandomAccessFile(file, "r").use { raf ->
            val length = raf.length()
            var pos = 0L
            val header = ByteArray(8)

            while (pos + 8 <= length) {
                raf.seek(pos)
                raf.readFully(header)
                var size = (beInt(header, 0).toLong() and 0xFFFFFFFFL)
                val type = String(header, 4, 4, Charsets.ISO_8859_1)
                var headerSize = 8L

                when (size) {
                    1L -> {
                        val extended = ByteArray(8)
                        raf.readFully(extended)
                        size = beLong(extended, 0)
                        headerSize = 16L
                    }
                    0L -> size = length - pos
                }
                if (size < headerSize) return null

                if (type == "moov") {
                    val payload = (size - headerSize).coerceAtMost(MAX_MOOV.toLong()).toInt()
                    if (payload <= 0) return null
                    val buffer = ByteArray(payload)
                    raf.seek(pos + headerSize)
                    raf.readFully(buffer)
                    return buffer
                }
                pos += size
            }
        }
        return null
    }

    /** The boxes directly inside a range — payload bounds, header already stepped over. */
    private fun boxes(buf: ByteArray, from: Int, to: Int): List<Box> {
        val found = mutableListOf<Box>()
        var i = from

        while (i + 8 <= to) {
            var size = (beInt(buf, i).toLong() and 0xFFFFFFFFL)
            val type = fourCC(buf, i + 4)
            var headerSize = 8

            when (size) {
                1L -> {
                    if (i + 16 > to) break
                    size = beLong(buf, i + 8)
                    headerSize = 16
                }
                0L -> size = (to - i).toLong()
            }
            if (size < headerSize || i + size > to) break

            found += Box(type, i + headerSize, (i + size).toInt())
            i += size.toInt()
        }
        return found
    }

    private fun fourCC(buf: ByteArray, at: Int): String =
        if (at + 4 <= buf.size) String(buf, at, 4, Charsets.ISO_8859_1) else ""

    private fun beInt(buf: ByteArray, at: Int): Int =
        ((buf[at].toInt() and 0xFF) shl 24) or
            ((buf[at + 1].toInt() and 0xFF) shl 16) or
            ((buf[at + 2].toInt() and 0xFF) shl 8) or
            (buf[at + 3].toInt() and 0xFF)

    private fun beLong(buf: ByteArray, at: Int): Long =
        (0 until 8).fold(0L) { acc, i -> (acc shl 8) or (buf[at + i].toLong() and 0xFF) }
}
