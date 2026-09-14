package com.pylo.yoinker.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** The system events Android still delivers to a sleeping app. */
class SystemEventsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext

        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Alarms and jobs don't survive a reboot or an update on their own.
                Automation.applySchedules(app)
                Hooks.refreshShareTargets(app)
                if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
                    RoutineEngine.fire(app, TriggerKind.BOOT_COMPLETED)
                }
            }

            Intent.ACTION_POWER_CONNECTED -> RoutineEngine.fire(app, TriggerKind.CHARGER_CONNECTED)

            Intent.ACTION_POWER_DISCONNECTED -> RoutineEngine.fire(app, TriggerKind.CHARGER_DISCONNECTED)
        }
    }
}
