package com.pylo.yoinker.automation

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.pylo.yoinker.download.Queue
import com.pylo.yoinker.download.YoinkService

/**
 * Yoinker's automation API for other apps — Tasker, MacroDroid, Automate, or
 * Modes & Routines by way of one of those. Every action is a plain broadcast:
 *
 *   am broadcast -a com.pylo.yoinker.action.YOINK \
 *      -n com.pylo.yoinker/.automation.AutomationReceiver \
 *      --es url "https://example.com/watch?v=…" --es mode music
 *
 * See docs/AUTOMATION.md for the full list.
 */
class AutomationReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext

        when (intent.action) {
            ACTION_YOINK -> {
                val url = intent.getStringExtra(EXTRA_URL)
                    ?: intent.getStringExtra(Intent.EXTRA_TEXT)
                    ?: intent.dataString
                Actions.yoink(app, url, resolveModeId(intent))
            }

            ACTION_YOINK_CLIPBOARD -> Actions.yoinkClipboard(app)

            ACTION_SET_MODE -> resolveModeId(intent)?.let { id ->
                Automation.setActiveMode(id)
                Hooks.refreshShareTargets(app)
            }

            ACTION_START_QUEUE -> {
                Queue.setPaused(false)
                if (Queue.pendingCount() > 0) YoinkService.kick(app)
            }

            ACTION_PAUSE_QUEUE -> Actions.perform(app, Action(ActionKind.PAUSE_QUEUE))

            ACTION_RESUME_QUEUE -> Queue.setPaused(false)

            ACTION_CLEAR_FINISHED -> Queue.clearFinished()

            ACTION_UPDATE_ENGINE -> Actions.perform(app, Action(ActionKind.UPDATE_ENGINE))

            ACTION_TRIGGER -> RoutineEngine.fire(
                app,
                TriggerKind.EXTERNAL,
                intent.getStringExtra(EXTRA_TAG).orEmpty(),
            )
        }
    }

    /** Accepts either a mode id or the name shown in the app — whichever is at hand. */
    private fun resolveModeId(intent: Intent): String? {
        val wanted = intent.getStringExtra(EXTRA_MODE)?.trim().orEmpty()
        if (wanted.isEmpty()) return null
        val modes = Automation.modes.value
        return modes.firstOrNull { it.id.equals(wanted, ignoreCase = true) }?.id
            ?: modes.firstOrNull { it.name.equals(wanted, ignoreCase = true) }?.id
    }

    companion object {
        const val ACTION_YOINK = "com.pylo.yoinker.action.YOINK"
        const val ACTION_YOINK_CLIPBOARD = "com.pylo.yoinker.action.YOINK_CLIPBOARD"
        const val ACTION_SET_MODE = "com.pylo.yoinker.action.SET_MODE"
        const val ACTION_START_QUEUE = "com.pylo.yoinker.action.START_QUEUE"
        const val ACTION_PAUSE_QUEUE = "com.pylo.yoinker.action.PAUSE_QUEUE"
        const val ACTION_RESUME_QUEUE = "com.pylo.yoinker.action.RESUME_QUEUE"
        const val ACTION_CLEAR_FINISHED = "com.pylo.yoinker.action.CLEAR_FINISHED"
        const val ACTION_UPDATE_ENGINE = "com.pylo.yoinker.action.UPDATE_ENGINE"
        const val ACTION_TRIGGER = "com.pylo.yoinker.action.TRIGGER"

        const val EXTRA_URL = "url"
        const val EXTRA_MODE = "mode"
        const val EXTRA_TAG = "tag"
    }
}
