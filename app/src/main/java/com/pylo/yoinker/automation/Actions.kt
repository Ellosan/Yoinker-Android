package com.pylo.yoinker.automation

import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.pylo.yoinker.R
import com.pylo.yoinker.core.Notifications
import com.pylo.yoinker.core.extractUrl
import com.pylo.yoinker.download.Queue
import com.pylo.yoinker.download.YoinkJob
import com.pylo.yoinker.download.YoinkService
import com.pylo.yoinker.engine.YoinkEngine
import com.pylo.yoinker.share.ShareActivity
import kotlin.concurrent.thread

/**
 * Every way Yoinker can be told to do something — a routine, a Quick Settings tile,
 * a Modes & Routines entry, another app's intent — lands here, so they all behave
 * identically.
 */
object Actions {

    /** Queues a link under a mode, and starts it unless that mode says to hold. */
    fun yoink(context: Context, rawUrl: String?, modeId: String? = null): Boolean {
        val url = extractUrl(rawUrl) ?: return false
        val mode = Automation.modeById(modeId ?: Automation.activeModeId.value)

        Queue.add(
            YoinkJob(
                url = url,
                format = mode.format,
                quality = mode.quality,
                modeId = mode.id,
            )
        )
        RoutineEngine.fire(context, TriggerKind.LINK_SHARED)

        if (!mode.holdInQueue && !Queue.paused.value) {
            YoinkService.kick(context)
        }
        return true
    }

    fun perform(context: Context, action: Action) {
        when (action.kind) {
            ActionKind.SET_MODE -> {
                Automation.setActiveMode(action.modeId)
                Hooks.refreshShareTargets(context)
            }

            ActionKind.YOINK_CLIPBOARD -> yoinkClipboard(context)

            ActionKind.START_QUEUE -> {
                Queue.setPaused(false)
                if (Queue.pendingCount() > 0) YoinkService.kick(context)
            }

            ActionKind.PAUSE_QUEUE -> {
                Queue.setPaused(true)
                YoinkService.kick(context, YoinkService.ACTION_PAUSE)
            }

            ActionKind.RESUME_QUEUE -> Queue.setPaused(false)

            ActionKind.CLEAR_FINISHED -> Queue.clearFinished()

            ActionKind.UPDATE_ENGINE -> thread(name = "yoink-engine-update") {
                YoinkEngine.updateEngine(context.applicationContext)
            }

            ActionKind.NOTIFY -> note(context, action.text.ifBlank { "Yoinker routine ran." })
        }
    }

    /**
     * From Android 10 the clipboard is only readable by an app that has focus, so
     * this goes through the share sheet activity rather than reading it from here.
     */
    fun yoinkClipboard(context: Context) {
        val intent = Intent(context, ShareActivity::class.java)
            .setAction(ShareActivity.ACTION_FROM_CLIPBOARD)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        runCatching { context.startActivity(intent) }
    }

    fun note(context: Context, text: String) {
        val notification = NotificationCompat.Builder(context, Notifications.CHANNEL_RESULTS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Yoinker")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .build()
        Notifications.post(context, Notifications.ID_RESULT_BASE + (text.hashCode() and 0xFF), notification)
    }
}
