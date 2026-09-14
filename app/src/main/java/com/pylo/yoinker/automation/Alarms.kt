package com.pylo.yoinker.automation

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.util.Calendar

/** Time-of-day routines. Inexact on purpose — nothing here needs to the second. */
object Alarms {

    private const val EXTRA_ROUTINE = "routineId"

    fun rescheduleAll(context: Context) {
        val manager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        Automation.routines.value
            .filter { it.trigger.kind == TriggerKind.TIME_OF_DAY }
            .forEach { routine ->
                val intent = pendingIntent(context, routine.id)
                if (!routine.enabled) {
                    manager.cancel(intent)
                    return@forEach
                }
                runCatching {
                    manager.setInexactRepeating(
                        AlarmManager.RTC_WAKEUP,
                        nextOccurrence(routine),
                        AlarmManager.INTERVAL_DAY,
                        intent,
                    )
                }
            }
    }

    private fun nextOccurrence(routine: Routine): Long {
        val now = Calendar.getInstance()
        val next = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, routine.trigger.hour)
            set(Calendar.MINUTE, routine.trigger.minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        if (next.timeInMillis <= now.timeInMillis) next.add(Calendar.DAY_OF_YEAR, 1)
        return next.timeInMillis
    }

    private fun pendingIntent(context: Context, routineId: String): PendingIntent {
        val intent = Intent(context, AlarmReceiver::class.java)
            .setAction("com.pylo.yoinker.alarm.$routineId")
            .putExtra(EXTRA_ROUTINE, routineId)
        return PendingIntent.getBroadcast(
            context,
            routineId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    internal fun handleFired(context: Context, intent: Intent) {
        intent.getStringExtra(EXTRA_ROUTINE)?.let { RoutineEngine.fireRoutine(context, it) }
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.handleFired(context.applicationContext, intent)
    }
}
