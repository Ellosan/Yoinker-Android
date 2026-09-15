package com.pylo.yoinker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every link that reaches Yoinker goes through here first — from the share sheet,
 * the clipboard, a text selection, or another app's intent. What arrives is rarely
 * a bare URL.
 */
class UrlsTest {

    @Test
    fun `takes a bare link`() {
        assertEquals("https://youtu.be/abc123", extractUrl("https://youtu.be/abc123"))
    }

    @Test
    fun `digs the link out of the sentence wrapped around it`() {
        assertEquals(
            "https://youtu.be/abc123",
            extractUrl("Watch this! https://youtu.be/abc123 it's great"),
        )
    }

    @Test
    fun `drops the full stop that ended the sentence, not the link`() {
        assertEquals("https://example.com/song", extractUrl("Have a listen: https://example.com/song."))
        assertEquals("https://example.com/song", extractUrl("https://example.com/song, then this"))
    }

    @Test
    fun `keeps a closing bracket that belongs to the link`() {
        // Wikipedia-style links really do end in a bracket, and trimming it blindly
        // turns a working link into a 404.
        val url = "https://en.wikipedia.org/wiki/Yoink_(disambiguation)"
        assertEquals(url, extractUrl(url))
    }

    @Test
    fun `still drops a bracket that was closing the prose`() {
        assertEquals(
            "https://example.com/clip",
            extractUrl("(the good bit is here https://example.com/clip)"),
        )
    }

    @Test
    fun `keeps query strings and tracking tails intact`() {
        val url = "https://www.youtube.com/watch?v=abc123&list=PL9&index=2"
        assertEquals(url, extractUrl("shared: $url"))
    }

    @Test
    fun `takes the first link when several are shared`() {
        assertEquals(
            "https://example.com/one",
            extractUrl("https://example.com/one and https://example.com/two"),
        )
    }

    @Test
    fun `handles http as well as https`() {
        assertEquals("http://example.com/x", extractUrl("http://example.com/x"))
    }

    @Test
    fun `says no to text with no link in it`() {
        assertNull(extractUrl("just some words"))
        assertNull(extractUrl(""))
        assertNull(extractUrl("   "))
        assertNull(extractUrl(null))
    }

    @Test
    fun `says no to a scheme with nothing after it`() {
        assertNull(extractUrl("https://"))
    }

    @Test
    fun `isYoinkable agrees with extractUrl`() {
        assertTrue(isYoinkable("see https://example.com/a"))
        assertFalse(isYoinkable("nothing here"))
    }

    @Test
    fun `hostOf names the site without the www`() {
        assertEquals("youtube.com", hostOf("https://www.youtube.com/watch?v=x"))
        assertEquals("instagram.com", hostOf("https://instagram.com/reel/x"))
        assertEquals("", hostOf("not a url"))
    }

    @Test
    fun `formats durations the way a video player would`() {
        assertEquals("0:42", formatDuration(42))
        assertEquals("6:42", formatDuration(402))
        assertEquals("1:03:20", formatDuration(3800))
        assertEquals("", formatDuration(0))
        assertEquals("", formatDuration(-5))
    }

    @Test
    fun `formats sizes and time remaining readably`() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("1.0 KB", formatBytes(1024))
        assertEquals("2.5 MB", formatBytes(2_621_440))
        assertEquals("", formatBytes(0))

        assertEquals("30s left", formatEta(30))
        assertEquals("2m 5s left", formatEta(125))
        assertEquals("", formatEta(0))
        assertEquals("", formatEta(-1))
    }
}
