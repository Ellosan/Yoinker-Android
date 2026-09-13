package com.pylo.yoinker.automation

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.pylo.yoinker.R
import com.pylo.yoinker.core.Device
import com.pylo.yoinker.core.Notifications
import com.pylo.yoinker.core.Prefs
import com.pylo.yoinker.ui.MainActivity

/**
 * Optional background watcher.
 *
 * Some of the things routines want to react to — Do Not Disturb flipping, headphones
 * going in — are only delivered to a running process; Android stopped waking apps for
 * them years ago. This service exists so those triggers work when Yoinker is closed,
 * and it is off until the user asks for it, because a permanent notification is a real
 * cost to charge someone who only ever yoinks from the share sheet.
 */
class AutomationService : Service() {

    private var lastDnd: Boolean? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val systemReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> onDndChanged()

                Intent.ACTION_HEADSET_PLUG ->
                    if (intent.getIntExtra("state", 0) == 1) {
                        RoutineEngine.fire(applicationContext, TriggerKind.HEADPHONES_CONNECTED)
                    }
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        lastDnd = Device.isDndOn(this)

        val filter = IntentFilter().apply {
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(systemReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(systemReceiver, filter)
        }

        watchNetwork()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            Notifications.ID_WATCHER,
            buildNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                0
            },
        )
        return START_STICKY
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(systemReceiver) }
        networkCallback?.let { callback ->
            val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            runCatching { cm?.unregisterNetworkCallback(callback) }
        }
        super.onDestroy()
    }

    private fun onDndChanged() {
        val now = Device.isDndOn(this)
        if (now == lastDnd) return
        lastDnd = now
        RoutineEngine.fire(
            applicationContext,
            if (now) TriggerKind.DND_ON else TriggerKind.DND_OFF,
        )
    }

    private fun watchNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            .build()
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                RoutineEngine.fire(applicationContext, TriggerKind.UNMETERED_CONNECTED)
            }
        }
        networkCallback = callback
        runCatching { cm.registerNetworkCallback(request, callback) }
    }

    private fun buildNotification(): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val mode = Automation.activeMode()
        return NotificationCompat.Builder(this, Notifications.CHANNEL_AUTOMATION)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Yoinker is watching for routines")
            .setContentText("${mode.emoji} ${mode.name} · tap to change")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        fun syncWithPreference(context: Context) {
            val intent = Intent(context, AutomationService::class.java)
            if (Prefs.watchInBackground) {
                runCatching {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(intent)
                    } else {
                        context.startService(intent)
                    }
                }
            } else {
                runCatching { context.stopService(intent) }
            }
        }
    }
}
