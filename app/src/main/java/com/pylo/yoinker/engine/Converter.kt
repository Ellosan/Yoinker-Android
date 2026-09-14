package com.pylo.yoinker.engine

import android.content.Context
import com.pylo.yoinker.core.Fmt
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlin.concurrent.thread

/** What a file turned out to contain. */
data class MediaProbe(
    val durationSec: Double,
    val videoCodec: String?,
    val audioCodec: String?,
    val width: Int,
    val height: Int,
) {
    val hasVideo: Boolean get() = videoCodec != null
    val isH264: Boolean get() = videoCodec == "h264"
    val isAac: Boolean get() = audioCodec == "aac"
}

/**
 * The built-in converter.
 *
 * The same ffmpeg that yt-dlp uses is already unpacked on the device, so this
 * drives it directly rather than shipping a second copy. It lives in the app's
 * native library directory — the one place Android still lets an app execute a
 * binary from — and needs the shared libraries that [YoinkEngine] unpacks, which
 * is why every entry point here initialises the engine first.
 */
object Converter {

    /** What to turn a file into. */
    sealed interface Target {
        val extension: String

        /** Audio only, for when you wanted the song and not the video. */
        data class Mp3(val bitrate: String = "192K") : Target {
            override val extension: String get() = "mp3"
        }

        /**
         * H.264 in MP4 — the combination every Android device plays. [maxHeight]
         * of 0 keeps the original size.
         */
        data class Mp4(val maxHeight: Int = 0, val crf: Int = 23) : Target {
            override val extension: String get() = "mp4"
        }
    }

    class Cancelled : Exception("Conversion stopped.")

    @Volatile
    private var current: Process? = null

    fun probe(context: Context, input: File): Result<MediaProbe> {
        if (!YoinkEngine.ensureInit(context)) {
            return Result.failure(IllegalStateException("The converter isn't ready yet."))
        }
        val ffprobe = binary(context, FFPROBE)
            ?: return Result.failure(IllegalStateException("Couldn't find ffprobe on this device."))

        return runCatching {
            val out = StringBuilder()
            val code = exec(
                context,
                ffprobe,
                listOf(
                    "-v", "error",
                    "-show_entries", "format=duration:stream=index,codec_type,codec_name,width,height",
                    "-of", "json",
                    input.absolutePath,
                ),
                onStdout = { out.appendLine(it) },
                onStderr = {},
            )
            if (code != 0) throw IllegalStateException("Couldn't read that file.")

            val parsed = json.decodeFromString<ProbeJson>(out.toString())
            val video = parsed.streams.firstOrNull { it.codecType == "video" }
            val audio = parsed.streams.firstOrNull { it.codecType == "audio" }
            MediaProbe(
                durationSec = parsed.format?.duration?.toDoubleOrNull() ?: 0.0,
                videoCodec = video?.codecName,
                audioCodec = audio?.codecName,
                width = video?.width ?: 0,
                height = video?.height ?: 0,
            )
        }
    }

    /**
     * Converts [input] and returns the finished file in [workDir].
     *
     * Streams that already suit the target are copied rather than re-encoded, so
     * "make this play" on a file that only had the wrong container costs seconds
     * instead of minutes.
     */
    fun convert(
        context: Context,
        input: File,
        target: Target,
        workDir: File,
        displayName: String,
        onProgress: (percent: Float) -> Unit,
    ): Result<File> {
        if (!YoinkEngine.ensureInit(context)) {
            return Result.failure(IllegalStateException("The converter isn't ready yet."))
        }
        val ffmpeg = binary(context, FFMPEG)
            ?: return Result.failure(IllegalStateException("Couldn't find ffmpeg on this device."))

        val probe = probe(context, input).getOrNull()
        if (target is Target.Mp3 && probe != null && probe.audioCodec == null) {
            return Result.failure(IllegalStateException("There's no audio in that file to convert."))
        }

        cancelled = false
        workDir.mkdirs()
        val output = File(workDir, "${sanitize(displayName)}.${target.extension}")
        output.delete()

        return runCatching {
            val errTail = StringBuilder()
            val duration = probe?.durationSec ?: 0.0

            val code = exec(
                context,
                ffmpeg,
                arguments(input, output, target, probe),
                onStdout = { line ->
                    // -progress writes key=value lines; out_time_us is the one that moves.
                    if (duration > 0 && line.startsWith("out_time_us=")) {
                        val micros = line.substringAfter('=').trim().toLongOrNull()
                        if (micros != null && micros >= 0) {
                            onProgress(((micros / 1_000_000.0) / duration * 100).toFloat().coerceIn(0f, 100f))
                        }
                    }
                },
                onStderr = { line ->
                    errTail.appendLine(line)
                    if (errTail.length > 4000) errTail.delete(0, errTail.length - 2000)
                },
            )

            when {
                code == 0 && output.isFile && output.length() > 0 -> output
                else -> {
                    output.delete()
                    throw IllegalStateException(friendlyError(errTail.toString()))
                }
            }
        }
    }

    fun cancel() {
        cancelled = true
        runCatching { current?.destroy() }
    }

    @Volatile
    private var cancelled = false

    // ---- argument building ---------------------------------------------------

    /** Split out from [convert] so the decisions it makes can be tested. */
    internal fun arguments(input: File, output: File, target: Target, probe: MediaProbe?): List<String> {
        val args = mutableListOf(
            "-hide_banner",
            "-nostdin",
            "-y",
            "-i", input.absolutePath,
            "-progress", "pipe:1",
            "-nostats",
        )

        when (target) {
            is Target.Mp3 -> {
                args += listOf("-vn", "-c:a", "libmp3lame", "-b:a", target.bitrate.lowercase())
                // Players are still happier with id3v2.3 than with 2.4.
                args += listOf("-id3v2_version", "3")
            }

            is Target.Mp4 -> {
                val needsResize = target.maxHeight > 0 && probe != null && probe.height > target.maxHeight
                // Copy the video only when it's already H.264 and staying the same size:
                // re-encoding a stream that was already fine costs minutes and quality.
                val copyVideo = probe?.isH264 == true && !needsResize

                if (copyVideo) {
                    args += listOf("-c:v", "copy")
                } else {
                    args += listOf(
                        "-c:v", "libx264",
                        "-preset", "veryfast",
                        "-crf", target.crf.toString(),
                        "-profile:v", "high",
                        "-pix_fmt", "yuv420p",
                    )
                    // libx264 refuses odd dimensions, which phone-shot video sometimes has.
                    args += listOf(
                        "-vf",
                        if (needsResize) {
                            "scale=-2:${target.maxHeight}"
                        } else {
                            "scale=trunc(iw/2)*2:trunc(ih/2)*2"
                        },
                    )
                }

                if (probe?.isAac == true) {
                    args += listOf("-c:a", "copy")
                } else if (probe?.audioCodec != null) {
                    args += listOf("-c:a", "aac", "-b:a", "192k")
                } else {
                    args += listOf("-an")
                }

                // Lets a player start before the whole file has been read.
                args += listOf("-movflags", "+faststart")
            }
        }

        args += output.absolutePath
        return args
    }

    /** The format a converted file comes out as, for the media store to file it under. */
    fun formatOf(target: Target): Fmt = when (target) {
        is Target.Mp3 -> Fmt.MP3
        is Target.Mp4 -> Fmt.MP4
    }

    // ---- running the binaries ------------------------------------------------

    private fun exec(
        context: Context,
        binary: File,
        args: List<String>,
        onStdout: (String) -> Unit,
        onStderr: (String) -> Unit,
    ): Int {
        val builder = ProcessBuilder(listOf(binary.absolutePath) + args)
        builder.environment().apply {
            put("LD_LIBRARY_PATH", libraryPath(context))
            put("TMPDIR", context.cacheDir.absolutePath)
        }

        val process = builder.start()
        current = process

        // stderr has to be drained on its own thread, or a chatty ffmpeg fills the
        // pipe buffer and the process stops dead waiting for someone to read it.
        val stderrPump = thread(name = "yoink-convert-err", isDaemon = true) {
            runCatching { process.errorStream.bufferedReader().forEachLine(onStderr) }
        }

        runCatching { process.inputStream.bufferedReader().forEachLine(onStdout) }

        val code = process.waitFor()
        stderrPump.join(2_000)
        current = null
        if (cancelled) throw Cancelled()
        return code
    }

    /**
     * ffmpeg's shared libraries live where the engine unpacked them. These paths
     * mirror youtubedl-android's own layout — if a future version moves them, the
     * binary lookup below fails loudly rather than silently producing nothing.
     */
    private fun libraryPath(context: Context): String {
        val packages = File(File(context.noBackupFilesDir, ENGINE_DIR), "packages")
        return listOf("python", "ffmpeg", "aria2c")
            .joinToString(":") { File(packages, "$it/usr/lib").absolutePath }
    }

    private fun binary(context: Context, name: String): File? =
        File(context.applicationInfo.nativeLibraryDir, name).takeIf { it.canExecute() }

    private fun sanitize(name: String): String =
        name.substringBeforeLast('.')
            .replace(Regex("""[/\\:*?"<>|]"""), "_")
            .trim()
            .take(120)
            .ifBlank { "converted" }

    private fun friendlyError(log: String): String {
        val line = log.lineSequence()
            .lastOrNull { it.contains("Error", true) || it.contains("Invalid", true) || it.contains("No such", true) }
            ?.trim()
        return when {
            line == null -> "The conversion failed."
            line.contains("Invalid data found", true) -> "That file isn't something ffmpeg can read."
            line.contains("No space left", true) -> "The phone ran out of space."
            else -> line.take(200)
        }
    }

    private const val FFMPEG = "libffmpeg.so"
    private const val FFPROBE = "libffprobe.so"
    private const val ENGINE_DIR = "youtubedl-android"

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class ProbeJson(
        val streams: List<StreamJson> = emptyList(),
        val format: FormatJson? = null,
    )

    @Serializable
    private data class StreamJson(
        @SerialName("codec_type") val codecType: String? = null,
        @SerialName("codec_name") val codecName: String? = null,
        val width: Int? = null,
        val height: Int? = null,
    )

    @Serializable
    private data class FormatJson(val duration: String? = null)
}
