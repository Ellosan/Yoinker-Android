package com.pylo.yoinker.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File

/**
 * The shapes here mirror a real Instagram download: a video track whose sample
 * entry is vp09, alongside an mp4a audio track. That file played its audio and
 * showed nothing, which is the case this probe exists to catch.
 */
class Mp4ProbeTest {

    @Test
    fun `reads H264 as the codec`() {
        val file = mp4(videoCodec = "avc1")
        assertEquals("avc1", Mp4Probe.videoCodec(file))
        assertTrue(Mp4Probe.isStockPlayable(Mp4Probe.videoCodec(file)))
    }

    @Test
    fun `flags VP9 in an mp4 as not playable`() {
        val file = mp4(videoCodec = "vp09")
        assertEquals("vp09", Mp4Probe.videoCodec(file))
        assertFalse(Mp4Probe.isStockPlayable("vp09"))
        assertEquals("VP9", Mp4Probe.codecName("vp09"))
    }

    @Test
    fun `flags AV1 in an mp4 as not playable`() {
        assertEquals("av01", Mp4Probe.videoCodec(mp4(videoCodec = "av01")))
        assertFalse(Mp4Probe.isStockPlayable("av01"))
    }

    @Test
    fun `skips the audio track when looking for the video codec`() {
        // The audio track comes first here, so a probe that just grabbed the first
        // stsd it found would answer "mp4a".
        assertEquals("avc1", Mp4Probe.videoCodec(mp4(videoCodec = "avc1", audioFirst = true)))
    }

    @Test
    fun `says nothing rather than guessing about a file it cannot read`() {
        val junk = File.createTempFile("yoink", ".mp4").apply { writeBytes(ByteArray(64) { 0x7F }) }
        assertNull(Mp4Probe.videoCodec(junk))
        // An unreadable file must not be reported as a problem.
        assertTrue(Mp4Probe.isStockPlayable(Mp4Probe.videoCodec(junk)))
    }

    @Test
    fun `handles a moov that sits after the media data`() {
        val file = mp4(videoCodec = "avc1", moovLast = true)
        assertEquals("avc1", Mp4Probe.videoCodec(file))
    }

    // ---- minimal MP4 construction -------------------------------------------

    private fun mp4(videoCodec: String, audioFirst: Boolean = false, moovLast: Boolean = false): File {
        val videoTrak = trak("vide", videoCodec)
        val audioTrak = trak("soun", "mp4a")
        val traks = if (audioFirst) audioTrak + videoTrak else videoTrak + audioTrak

        val moov = box("moov", traks)
        val mdat = box("mdat", ByteArray(512))
        val ftyp = box("ftyp", "isom".toByteArray(Charsets.ISO_8859_1) + ByteArray(8))

        val bytes = if (moovLast) ftyp + mdat + moov else ftyp + moov + mdat
        return File.createTempFile("yoink", ".mp4").apply { writeBytes(bytes) }
    }

    private fun trak(handler: String, codec: String): ByteArray {
        val hdlr = box(
            "hdlr",
            ByteArray(4) + ByteArray(4) + handler.toByteArray(Charsets.ISO_8859_1) + ByteArray(12),
        )
        // stsd: version+flags, entry count, then one [size][format] entry.
        val entry = box(codec, ByteArray(70))
        val stsd = box("stsd", ByteArray(4) + intBytes(1) + entry)
        val stbl = box("stbl", stsd)
        val minf = box("minf", stbl)
        val mdia = box("mdia", hdlr + minf)
        return box("trak", mdia)
    }

    private fun box(type: String, payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write(intBytes(payload.size + 8))
        out.write(type.toByteArray(Charsets.ISO_8859_1))
        out.write(payload)
        return out.toByteArray()
    }

    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(),
        (value ushr 16).toByte(),
        (value ushr 8).toByte(),
        value.toByte(),
    )
}
