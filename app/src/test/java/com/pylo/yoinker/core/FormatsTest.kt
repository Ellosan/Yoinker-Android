package com.pylo.yoinker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsTest {

    @Test
    fun `reads a format back from the id stored on disk`() {
        assertEquals(Fmt.MP3, Fmt.of("mp3"))
        assertEquals(Fmt.MP4, Fmt.of("MP4"))
    }

    @Test
    fun `falls back to audio rather than failing on a value it does not know`() {
        assertEquals(Fmt.MP3, Fmt.of("ogg"))
        assertEquals(Fmt.MP3, Fmt.of(null))
    }

    @Test
    fun `a quality from the other format is replaced, not carried across`() {
        // A saved mode switched from MP4 to MP3 would otherwise ask yt-dlp for
        // "720" kbps audio.
        assertEquals("192K", Qualities.sanitize(Fmt.MP3, "720"))
        assertEquals("best", Qualities.sanitize(Fmt.MP4, "320K"))
    }

    @Test
    fun `a quality that does belong to the format survives`() {
        assertEquals("320K", Qualities.sanitize(Fmt.MP3, "320K"))
        assertEquals("1080", Qualities.sanitize(Fmt.MP4, "1080"))
    }

    @Test
    fun `missing quality becomes the default for that format`() {
        assertEquals("192K", Qualities.sanitize(Fmt.MP3, null))
        assertEquals("best", Qualities.sanitize(Fmt.MP4, null))
    }

    @Test
    fun `every offered quality is one sanitize will accept`() {
        Fmt.entries.forEach { fmt ->
            Qualities.forFormat(fmt).forEach { option ->
                assertEquals(option.value, Qualities.sanitize(fmt, option.value))
            }
        }
    }

    @Test
    fun `each format offers its own choices and a default among them`() {
        Fmt.entries.forEach { fmt ->
            val values = Qualities.forFormat(fmt).map { it.value }
            assertTrue("$fmt has no qualities", values.isNotEmpty())
            assertTrue("default not offered for $fmt", Qualities.default(fmt) in values)
        }
    }
}
