package com.pylo.yoinker.convert

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.provider.OpenableColumns
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pylo.yoinker.R
import com.pylo.yoinker.core.Notifications
import com.pylo.yoinker.core.formatBytes
import com.pylo.yoinker.download.Exporter
import com.pylo.yoinker.engine.Converter
import com.pylo.yoinker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Runs one conversion, in the foreground, because re-encoding a video takes long
 * enough that Android would otherwise kill the app halfway through.
 */
class ConvertService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var lastNotifyAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        promote(notification("Converting…", null, null))

        if (intent?.action == ACTION_CANCEL) {
            Converter.cancel()
            return START_NOT_STICKY
        }

        val source = intent?.getParcelableExtraCompat(EXTRA_URI)
        if (source == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        scope.launch { run(source, targetFrom(intent)) }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun run(source: Uri, target: Converter.Target) {
        val name = displayName(source)
        val workDir = File(cacheDir, "convert").apply { deleteRecursively(); mkdirs() }

        try {
            // ffmpeg can't read a content:// Uri, so the input comes across first.
            ConvertState.set(ConvertState.Phase.Reading)
            notify("Reading ${name}…", null)
            val input = copyIn(source, workDir, name)
                ?: throw IllegalStateException("Couldn't read that file.")

            ConvertState.set(ConvertState.Phase.Working(0f))
            val result = Converter.convert(
                context = applicationContext,
                input = input,
                target = target,
                workDir = File(workDir, "out"),
                displayName = name,
            ) { percent ->
                ConvertState.set(ConvertState.Phase.Working(percent))
                throttledNotify(name, percent)
            }

            val converted = result.getOrElse { error ->
                if (error is Converter.Cancelled) {
                    ConvertState.set(ConvertState.Phase.Idle)
                    Notifications.cancel(applicationContext, Notifications.ID_CONVERT)
                    return
                }
                throw error
            }

            val saved = Exporter.export(applicationContext, converted, Converter.formatOf(target))
            ConvertState.set(ConvertState.Phase.Done(saved.displayName, saved.uri?.toString()))
            postDone(saved.displayName, saved.sizeBytes, saved.uri, Converter.formatOf(target))
        } catch (e: Exception) {
            val message = e.message ?: "The conversion failed."
            ConvertState.set(ConvertState.Phase.Failed(message))
            postFailed(message)
        } finally {
            workDir.deleteRecursively()
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun copyIn(source: Uri, workDir: File, name: String): File? {
        val target = File(workDir, "in_${name.takeLast(80).replace(Regex("""[/\\]"""), "_")}")
        return runCatching {
            contentResolver.openInputStream(source)?.use { input ->
                target.outputStream().use { input.copyTo(it) }
            } ?: return null
            target
        }.getOrNull()
    }

    private fun displayName(uri: Uri): String {
        val fromProvider = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment?.substringAfterLast('/') ?: "file"
    }

    // ---- notifications -------------------------------------------------------

    private fun promote(notification: Notification) {
        runCatching {
            ServiceCompat.startForeground(
                this,
                Notifications.ID_CONVERT,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                },
            )
        }
    }

    private fun throttledNotify(name: String, percent: Float) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < 700) return
        lastNotifyAt = now
        notify(name, percent)
    }

    private fun notify(title: String, percent: Float?) {
        val n = notification(
            title.take(60),
            percent?.let { "Converting… ${it.toInt()}%" } ?: "Converting…",
            percent,
        )
        promote(n)
        Notifications.post(applicationContext, Notifications.ID_CONVERT, n)
    }

    private fun notification(title: String, text: String?, percent: Float?): Notification {
        val stop = PendingIntent.getService(
            this,
            0,
            Intent(this, ConvertService::class.java).setAction(ACTION_CANCEL),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, Notifications.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp())
            .addAction(R.drawable.ic_stop, "Stop", stop)
            .apply {
                if (percent == null || percent <= 0f) setProgress(0, 0, true)
                else setProgress(100, percent.toInt(), false)
            }
            .build()
    }

    private fun postDone(name: String, size: Long, uri: Uri?, fmt: com.pylo.yoinker.core.Fmt) {
        val tap = if (uri != null) {
            PendingIntent.getActivity(
                this,
                uri.hashCode(),
                Intent(Intent.ACTION_VIEW)
                    .setDataAndType(uri, Exporter.mimeFor(fmt))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        } else {
            openApp()
        }

        Notifications.post(
            applicationContext,
            Notifications.ID_CONVERT_RESULT,
            NotificationCompat.Builder(this, Notifications.CHANNEL_RESULTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Converted: ${name.take(50)}")
                .setContentText(formatBytes(size))
                .setAutoCancel(true)
                .setContentIntent(tap)
                .build(),
        )
    }

    private fun postFailed(message: String) {
        Notifications.post(
            applicationContext,
            Notifications.ID_CONVERT_RESULT,
            NotificationCompat.Builder(this, Notifications.CHANNEL_RESULTS)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Couldn't convert that one")
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build(),
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .putExtra("tab", "convert")
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    @Suppress("DEPRECATION")
    private fun Intent.getParcelableExtraCompat(name: String): Uri? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(name, Uri::class.java)
        } else {
            getParcelableExtra(name)
        }

    private fun targetFrom(intent: Intent): Converter.Target =
        if (intent.getStringExtra(EXTRA_TARGET) == "mp3") {
            Converter.Target.Mp3(intent.getStringExtra(EXTRA_BITRATE) ?: "192K")
        } else {
            Converter.Target.Mp4(maxHeight = intent.getIntExtra(EXTRA_MAX_HEIGHT, 0))
        }

    companion object {
        const val ACTION_CANCEL = "com.pylo.yoinker.convert.CANCEL"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_TARGET = "target"
        private const val EXTRA_BITRATE = "bitrate"
        private const val EXTRA_MAX_HEIGHT = "maxHeight"

        fun start(context: Context, source: Uri, target: Converter.Target): Boolean {
            val intent = Intent(context, ConvertService::class.java)
                .putExtra(EXTRA_URI, source)
                .apply {
                    when (target) {
                        is Converter.Target.Mp3 -> {
                            putExtra(EXTRA_TARGET, "mp3")
                            putExtra(EXTRA_BITRATE, target.bitrate)
                        }
                        is Converter.Target.Mp4 -> {
                            putExtra(EXTRA_TARGET, "mp4")
                            putExtra(EXTRA_MAX_HEIGHT, target.maxHeight)
                        }
                    }
                }
            return runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            }.getOrDefault(false)
        }

        fun stop(context: Context) {
            Converter.cancel()
            runCatching {
                context.startService(Intent(context, ConvertService::class.java).setAction(ACTION_CANCEL))
            }
        }
    }
}
