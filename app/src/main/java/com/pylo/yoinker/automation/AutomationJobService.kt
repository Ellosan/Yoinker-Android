package com.pylo.yoinker.automation

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.pylo.yoinker.download.Queue
import com.pylo.yoinker.download.YoinkService

/**
 * The part of the automation that has to survive Yoinker not running.
 *
 * Connectivity broadcasts stopped reaching manifest receivers years ago, so the
 * "Wi-Fi connected" trigger is a scheduled job with an unmetered-network constraint
 * instead: the system starts us when Wi-Fi actually turns up.
 */
class AutomationJobService : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        when (params.jobId) {
            JOB_UNMETERED -> {
                RoutineEngine.fire(applicationContext, TriggerKind.UNMETERED_CONNECTED)
                if (Queue.pendingCount() > 0 && !Queue.paused.value) YoinkService.kick(applicationContext)
                // One-shot: reschedule so the next Wi-Fi connection wakes us too.
                scheduleUnmeteredWake(applicationContext)
            }

            JOB_QUEUE -> if (Queue.pendingCount() > 0 && !Queue.paused.value) {
                YoinkService.kick(applicationContext)
            }
        }
        return false
    }

    override fun onStopJob(params: JobParameters): Boolean = false

    companion object {
        private const val JOB_UNMETERED = 1101
        private const val JOB_QUEUE = 1102

        /** Wakes us the next time the phone is on an unmetered connection. */
        fun scheduleUnmeteredWake(context: Context) {
            schedule(context, JOB_UNMETERED) {
                setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED)
                setPersisted(true)
            }
        }

        /**
         * Retry hook for when a routine wanted to start downloading while Yoinker was
         * in the background and Android refused the foreground service. Expedited, so
         * the retry is allowed to start one.
         */
        fun scheduleQueueWake(context: Context) {
            schedule(context, JOB_QUEUE) {
                setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setExpedited(true)
                } else {
                    setOverrideDeadline(60_000)
                }
            }
        }

        fun syncWithRoutines(context: Context) {
            val wantsWifi = Automation.routines.value.any {
                it.enabled && it.trigger.kind == TriggerKind.UNMETERED_CONNECTED
            }
            if (wantsWifi) scheduleUnmeteredWake(context) else cancel(context, JOB_UNMETERED)
        }

        private fun schedule(context: Context, id: Int, configure: JobInfo.Builder.() -> Unit) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            val builder = JobInfo.Builder(id, ComponentName(context, AutomationJobService::class.java))
                .apply(configure)
            runCatching { scheduler.schedule(builder.build()) }
        }

        private fun cancel(context: Context, id: Int) {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler ?: return
            runCatching { scheduler.cancel(id) }
        }
    }
}
