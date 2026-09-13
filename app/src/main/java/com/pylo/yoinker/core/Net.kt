package com.pylo.yoinker.core

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.app.NotificationManager

/** Small read-only view of the device state that modes and routines care about. */
object Device {

    fun isOnline(context: Context): Boolean = capabilities(context)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true

    /** Wi-Fi, in the way people mean it: a connection nobody is billing by the megabyte. */
    fun isUnmetered(context: Context): Boolean = capabilities(context)
        ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED) == true

    fun isCharging(context: Context): Boolean {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return false
        return bm.isCharging
    }

    fun batteryPercent(context: Context): Int {
        val bm = context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return -1
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    /**
     * True when the phone is in any Do Not Disturb state. Samsung's Modes — Sleep,
     * Driving, Work — turn this on, which is what lets Yoinker follow along with them.
     */
    fun isDndOn(context: Context): Boolean {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return false
        return runCatching {
            nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL &&
                nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN
        }.getOrDefault(false)
    }

    private fun capabilities(context: Context): NetworkCapabilities? {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
        return runCatching { cm.getNetworkCapabilities(cm.activeNetwork) }.getOrNull()
    }
}
