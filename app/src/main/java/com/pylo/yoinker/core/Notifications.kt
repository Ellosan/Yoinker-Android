package com.pylo.yoinker.core

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pylo.yoinker.R

object Notifications {

    const val CHANNEL_DOWNLOADS = "downloads"
    const val CHANNEL_RESULTS = "results"
    const val CHANNEL_AUTOMATION = "automation"

    const val ID_PROGRESS = 1001
    const val ID_WATCHER = 1002
    const val ID_BLOCKED = 1003
    const val ID_CONVERT = 1004
    const val ID_CONVERT_RESULT = 1005
    const val ID_RESULT_BASE = 2000

    fun createChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return

        val downloads = NotificationChannel(
            CHANNEL_DOWNLOADS,
            context.getString(R.string.channel_downloads),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.channel_downloads_desc)
            setShowBadge(false)
        }

        val results = NotificationChannel(
            CHANNEL_RESULTS,
            context.getString(R.string.channel_results),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply { description = context.getString(R.string.channel_results_desc) }

        val automation = NotificationChannel(
            CHANNEL_AUTOMATION,
            context.getString(R.string.channel_automation),
            NotificationManager.IMPORTANCE_MIN,
        ).apply {
            description = context.getString(R.string.channel_automation_desc)
            setShowBadge(false)
        }

        manager.createNotificationChannels(listOf(downloads, results, automation))
    }

    /** Posting is best-effort: on Android 13+ the user may simply have said no. */
    fun post(context: Context, id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }

    fun cancel(context: Context, id: Int) {
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
    }
}
