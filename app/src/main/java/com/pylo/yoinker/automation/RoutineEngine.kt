package com.pylo.yoinker.automation

import android.content.Context
import com.pylo.yoinker.core.Device
import com.pylo.yoinker.download.Queue

/**
 * Runs the user's routines. Something happens, every enabled routine listening for
 * it checks its conditions, and the ones that still agree run their actions.
 */
object RoutineEngine {

    fun fire(context: Context, kind: TriggerKind, tag: String? = null) {
        val app = context.applicationContext
        Automation.routinesFor(kind)
            .filter { kind != TriggerKind.EXTERNAL || it.trigger.tag.equals(tag, ignoreCase = true) }
            .forEach { run(app, it) }
    }

    /** Alarms name the routine directly — a time routine only ever means itself. */
    fun fireRoutine(context: Context, routineId: String) {
        val routine = Automation.routines.value.firstOrNull { it.id == routineId && it.enabled } ?: return
        if (!dayMatches(routine)) return
        run(context.applicationContext, routine)
    }

    private fun run(context: Context, routine: Routine) {
        if (!conditionsPass(context, routine)) return
        routine.actions.forEach { Actions.perform(context, it) }
        Automation.markRoutineRun(routine.id)
    }

    private fun conditionsPass(context: Context, routine: Routine): Boolean =
        routine.conditions.all { condition ->
            when (condition.kind) {
                ConditionKind.ON_WIFI -> Device.isUnmetered(context)
                ConditionKind.ON_MOBILE -> Device.isOnline(context) && !Device.isUnmetered(context)
                ConditionKind.CHARGING -> Device.isCharging(context)
                ConditionKind.NOT_CHARGING -> !Device.isCharging(context)
                ConditionKind.BATTERY_ABOVE -> Device.batteryPercent(context) >= condition.number
                ConditionKind.DND_ON -> Device.isDndOn(context)
                ConditionKind.DND_OFF -> !Device.isDndOn(context)
                ConditionKind.QUEUE_NOT_EMPTY -> Queue.pendingCount() > 0
                ConditionKind.MODE_IS -> Automation.activeModeId.value == condition.modeId
            }
        }

    /** An empty day list means every day. */
    private fun dayMatches(routine: Routine): Boolean {
        val days = routine.trigger.days
        if (days.isEmpty()) return true
        val today = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_WEEK)
        return today in days
    }
}
