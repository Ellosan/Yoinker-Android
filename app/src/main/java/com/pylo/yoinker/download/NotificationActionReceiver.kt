package com.pylo.yoinker.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pylo.yoinker.core.Notifications

/** The Stop button on the progress notification. */
class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ACTION_STOP -> {
                Queue.running()?.let { Queue.update(it.id) { job -> job.copy(state = JobState.CANCELED) } }
                YoinkService.kick(context, YoinkService.ACTION_CANCEL_CURRENT)
                Notifications.cancel(context, Notifications.ID_PROGRESS)
            }
        }
    }

    companion object {
        const val ACTION_STOP = "com.pylo.yoinker.notification.STOP"
    }
}
