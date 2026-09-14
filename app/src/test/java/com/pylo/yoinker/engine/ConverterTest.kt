package com.pylo.yoinker.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The converter's job is to re-encode as little as possible: re-encoding a stream
 * that was already fine costs minutes of phone battery and a generation of quality.
 */
class ConverterTest {

    private val input = File("/tmp/in.mp4")
    private val output = File("/tmp/out.mp4")

    private fun args(target: Converter.Target, probe: MediaProbe?) =
        Converter.arguments(input, output, target, probe).joinToString(" ")

    private fun probe(video: String?, audio: String? = "aac", width: Int = 1920, height: Int = 1080) =
        MediaProbe(durationSec = 10.0, videoCodec = video, audioCodec = audio, width = width, height = height)

    @Test
    fun `copies H264 video instead of re-encoding it`() {
        val line = args(Converter.Target.Mp4(), probe("h264"))
        assertTrue(line.contains("-c:v copy"))
        assertFalse(line.contains("libx264"))
    }

    @Test
    fun `re-encodes VP9 to H264`() {
        val line = args(Converter.Target.Mp4(), probe("vp9"))
        assertTrue(line.contains("-c:v libx264"))
        assertFalse(line.contains("-c:v copy"))
    }

    @Test
    fun `re-encodes AV1 to H264`() {
        assertTrue(args(Converter.Target.Mp4(), probe("av1")).contains("-c:v libx264"))
    }

    @Test
    fun `re-encodes H264 when it has to be made smaller`() {
        val line = args(Converter.Target.Mp4(maxHeight = 720), probe("h264", height = 1080))
        assertTrue(line.contains("-c:v libx264"))
        assertTrue(line.contains("scale=-2:720"))
    }

    @Test
    fun `leaves H264 alone when it is already under the size asked for`() {
        val line = args(Converter.Target.Mp4(maxHeight = 1080), probe("h264", height = 720))
        assertTrue(line.contains("-c:v copy"))
        assertFalse(line.contains("scale=-2:1080"))
    }

    @Test
    fun `keeps dimensions even when re-encoding, because libx264 rejects odd ones`() {
        val line = args(Converter.Target.Mp4(), probe("vp9", width = 481, height = 495))
        assertTrue(line.contains("scale=trunc(iw/2)*2:trunc(ih/2)*2"))
    }

    @Test
    fun `copies AAC audio but re-encodes anything else`() {
        assertTrue(args(Converter.Target.Mp4(), probe("vp9", audio = "aac")).contains("-c:a copy"))
        assertTrue(args(Converter.Target.Mp4(), probe("vp9", audio = "opus")).contains("-c:a aac"))
    }

    @Test
    fun `drops the audio track when there isn't one`() {
        assertTrue(args(Converter.Target.Mp4(), probe("vp9", audio = null)).contains("-an"))
    }

    @Test
    fun `writes mp4 headers up front so playback can start early`() {
        assertTrue(args(Converter.Target.Mp4(), probe("h264")).contains("-movflags +faststart"))
    }

    @Test
    fun `extracts audio only for mp3, at the bitrate asked for`() {
        val line = args(Converter.Target.Mp3("320K"), probe("h264"))
        assertTrue(line.contains("-vn"))
        assertTrue(line.contains("-c:a libmp3lame"))
        assertTrue(line.contains("-b:a 320k"))
        assertFalse(line.contains("libx264"))
    }

    @Test
    fun `still produces usable arguments when the file could not be probed`() {
        val line = args(Converter.Target.Mp4(), null)
        // Nothing is known, so nothing is assumed safe to copy.
        assertTrue(line.contains("-c:v libx264"))
        assertTrue(line.contains("-an"))
    }

    @Test
    fun `always reports progress on stdout so it can be followed`() {
        assertTrue(args(Converter.Target.Mp3(), probe("h264")).contains("-progress pipe:1"))
        assertTrue(args(Converter.Target.Mp4(), probe("h264")).contains("-progress pipe:1"))
    }
}
