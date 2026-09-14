package com.pylo.yoinker.download

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pylo.yoinker.R
import com.pylo.yoinker.automation.Automation
import com.pylo.yoinker.automation.AutomationJobService
import com.pylo.yoinker.automation.RoutineEngine
import com.pylo.yoinker.automation.TriggerKind
import com.pylo.yoinker.core.Device
import com.pylo.yoinker.core.Fmt
import com.pylo.yoinker.core.Notifications
import com.pylo.yoinker.core.formatBytes
import com.pylo.yoinker.engine.Mp4Probe
import com.pylo.yoinker.engine.YoinkEngine
import com.pylo.yoinker.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Works the queue, one job at a time, in the foreground so Android doesn't stop it
 * halfway through a 200 MB video.
 */
class YoinkService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null

    @Volatile
    private var runningJobId: String? = null

    @Volatile
    private var lastNotifyAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android gives a foreground service about five seconds to show itself.
        promote(buildProgressNotification(title = "Getting ready…", text = null, percent = null))

        when (intent?.action) {
            ACTION_CANCEL_CURRENT -> cancelCurrent()
            ACTION_CANCEL_JOB -> intent.getStringExtra(EXTRA_JOB_ID)?.let { cancelJob(it) }
            ACTION_PAUSE -> {
                Queue.setPaused(true)
                cancelCurrent()
                stopIfIdle()
                return START_NOT_STICKY
            }
        }

        startWorker()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        worker?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private fun startWorker() {
        if (worker?.isActive == true) return
        worker = scope.launch { drainQueue() }
    }

    private suspend fun drainQueue() {
        YoinkEngine.ensureInit(applicationContext)

        while (true) {
            if (Queue.paused.value) break
            val job = Queue.nextQueued() ?: break

            val mode = Automation.modeById(job.modeId)
            if (mode.wifiOnly && !Device.isUnmetered(applicationContext)) {
                // Hold it rather than burning mobile data, and ask the system to wake
                // us when Wi-Fi turns up.
                AutomationJobService.scheduleUnmeteredWake(applicationContext)
                postWaitingForWifi()
                break
            }

            runJob(job)
        }

        stopIfIdle()
    }

    private fun runJob(job: YoinkJob) {
        runningJobId = job.id
        Queue.update(job.id) { it.copy(state = JobState.RUNNING, progress = 0f, error = null) }

        // Every job downloads into its own scratch directory, so whatever lands there
        // is unambiguously this job's file — even if yt-dlp renames it on the way.
        val workDir = File(cacheDir, "yoink/${job.id}")
        workDir.deleteRecursively()
        workDir.mkdirs()

        notifyProgress(job.label, 0f, "Starting…")

        // Jobs that came from a routine or an instant share were never previewed, so
        // the notification would otherwise read as a bare URL.
        val named = if (job.title == null) nameJob(job) else job

        val result = YoinkEngine.download(
            context = applicationContext,
            url = job.url,
            fmt = job.format,
            quality = job.quality,
            outDir = workDir,
            processId = job.id,
        ) { percent, eta, _ ->
            Queue.update(job.id) { it.copy(progress = percent.coerceIn(0f, 100f), etaSeconds = eta) }
            throttledProgress(job.label, percent, eta)
        }

        runningJobId = null

        result.onSuccess { file ->
            finishJob(named, file)
        }.onFailure { error ->
            workDir.deleteRecursively()
            val canceled = Queue.get(job.id)?.state == JobState.CANCELED
            if (canceled) {
                Notifications.cancel(applicationContext, Notifications.ID_PROGRESS)
                return@onFailure
            }
            val message = error.message ?: "Download failed."
            Queue.update(job.id) { it.copy(state = JobState.FAILED, error = message) }
            postResult(job, ok = false, text = message, uri = null)
            RoutineEngine.fire(applicationContext, TriggerKind.DOWNLOAD_FAILED)
        }
    }

    private fun finishJob(job: YoinkJob, file: File) {
        notifyProgress(job.label, 100f, if (job.format.isAudio) "Saving MP3…" else "Saving MP4…")

        // A file can be valid and still show nothing — VP9 or AV1 inside an MP4 is
        // legal, and most Android players won't draw it. The format selector should
        // have ruled that out; this catches the source that leaves no other choice.
        val warning = if (job.format.isAudio) {
            null
        } else {
            val codec = Mp4Probe.videoCodec(file)
            if (Mp4Probe.isStockPlayable(codec)) {
                null
            } else {
                "This one is ${Mp4Probe.codecName(codec)} video — the source offered nothing " +
                    "else, and some players will show no picture."
            }
        }

        val saved = runCatching { Exporter.export(applicationContext, file, job.format) }
        File(cacheDir, "yoink/${job.id}").deleteRecursively()

        saved.onSuccess { out ->
            Queue.update(job.id) {
                it.copy(
                    state = JobState.DONE,
                    progress = 100f,
                    etaSeconds = -1L,
                    savedUri = out.uri?.toString(),
                    savedName = out.displayName,
                    sizeBytes = out.sizeBytes,
                    warning = warning,
                )
            }
            val text = "${out.displayName} · ${formatBytes(out.sizeBytes)}" +
                (warning?.let { "\n⚠ $it" } ?: "")
            postResult(job, ok = true, text = text, uri = out.uri)
            RoutineEngine.fire(applicationContext, TriggerKind.DOWNLOAD_FINISHED)
        }.onFailure { error ->
            val message = "Downloaded, but couldn't save it: ${error.message}"
            Queue.update(job.id) { it.copy(state = JobState.FAILED, error = message) }
            postResult(job, ok = false, text = message, uri = null)
            RoutineEngine.fire(applicationContext, TriggerKind.DOWNLOAD_FAILED)
        }
    }

    /** Best-effort: a title makes the notification readable, it isn't worth failing over. */
    private fun nameJob(job: YoinkJob): YoinkJob {
        val info = YoinkEngine.peek(applicationContext, job.url).getOrNull() ?: return job
        Queue.update(job.id) {
            it.copy(
                title = info.title,
                uploader = info.uploader,
                thumbnail = info.thumbnail,
                durationSec = info.durationSec,
            )
        }
        notifyProgress(info.title, 0f, "Starting…")
        return Queue.get(job.id) ?: job
    }

    private fun cancelCurrent() {
        runningJobId?.let { cancelJob(it) }
    }

    private fun cancelJob(id: String) {
        Queue.update(id) { if (it.isFinished) it else it.copy(state = JobState.CANCELED) }
        YoinkEngine.cancel(id)
    }

    private fun stopIfIdle() {
        if (Queue.pendingCount() == 0 || Queue.paused.value) {
            Notifications.cancel(applicationContext, Notifications.ID_PROGRESS)
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    // ---- notifications ------------------------------------------------------

    private fun promote(notification: Notification) {
        runCatching {
            ServiceCompat.startForeground(
                this,
                Notifications.ID_PROGRESS,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                },
            )
        }
    }

    private fun throttledProgress(title: String, percent: Float, eta: Long) {
        val now = System.currentTimeMillis()
        if (now - lastNotifyAt < 700) return
        lastNotifyAt = now
        val etaText = com.pylo.yoinker.core.formatEta(eta)
        notifyProgress(title, percent, if (etaText.isEmpty()) "Downloading…" else "Downloading… $etaText")
    }

    private fun notifyProgress(title: String, percent: Float, text: String?) {
        val notification = buildProgressNotification(title, text, percent)
        promote(notification)
        Notifications.post(applicationContext, Notifications.ID_PROGRESS, notification)
    }

    private fun buildProgressNotification(title: String, text: String?, percent: Float?): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getBroadcast(
            this,
            1,
            Intent(this, NotificationActionReceiver::class.java).setAction(NotificationActionReceiver.ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val remaining = Queue.pendingCount()
        return NotificationCompat.Builder(this, Notifications.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.take(60))
            .setContentText(text)
            .setSubText(if (remaining > 1) "$remaining in queue" else null)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(open)
            .addAction(R.drawable.ic_stop, "Stop", stop)
            .apply {
                if (percent == null || percent <= 0f) {
                    setProgress(0, 0, true)
                } else {
                    setProgress(100, percent.toInt(), false)
                }
            }
            .build()
    }

    private fun postResult(job: YoinkJob, ok: Boolean, text: String, uri: Uri?) {
        val quiet = Automation.modeById(job.modeId).quiet
        val tap = if (ok && uri != null) openFileIntent(uri, job.format) else openAppIntent()

        val notification = NotificationCompat.Builder(this, Notifications.CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(if (ok) "Yoinked: ${job.label.take(50)}" else "Couldn't yoink that one")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(tap)
            .setSilent(quiet)
            .build()

        Notifications.post(applicationContext, Notifications.ID_RESULT_BASE + (job.id.hashCode() and 0xFFF), notification)
    }

    private fun postWaitingForWifi() {
        val notification = NotificationCompat.Builder(this, Notifications.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Waiting for Wi-Fi")
            .setContentText("${Queue.pendingCount()} queued. This mode only downloads on Wi-Fi.")
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .setSilent(true)
            .build()
        Notifications.post(applicationContext, Notifications.ID_BLOCKED, notification)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun openFileIntent(uri: Uri, fmt: Fmt): PendingIntent {
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, Exporter.mimeFor(fmt))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            this,
            uri.hashCode(),
            view,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    companion object {
        const val ACTION_CANCEL_CURRENT = "com.pylo.yoinker.service.CANCEL_CURRENT"
        const val ACTION_CANCEL_JOB = "com.pylo.yoinker.service.CANCEL_JOB"
        const val ACTION_PAUSE = "com.pylo.yoinker.service.PAUSE"
        const val EXTRA_JOB_ID = "jobId"

        /**
         * Nudges the queue along. From Android 12 the system refuses foreground
         * services started from the background, which is exactly when a routine
         * fires — so a refusal leaves the jobs queued and says so, rather than
         * crashing or silently dropping them.
         */
        fun kick(context: Context, action: String? = null, jobId: String? = null): Boolean {
            val intent = Intent(context, YoinkService::class.java).apply {
                action?.let { setAction(it) }
                jobId?.let { putExtra(EXTRA_JOB_ID, it) }
            }
            return try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
                true
            } catch (e: Exception) {
                AutomationJobService.scheduleQueueWake(context)
                false
            }
        }
    }
}
