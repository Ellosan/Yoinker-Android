package com.pylo.yoinker.automation

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import com.pylo.yoinker.core.Prefs

/**
 * One action, no window. This is what Modes & Routines actually opens when a routine
 * says "open Yoinker: start queue" — it runs the action behind the alias it was
 * launched through and closes again, so the automation never lands the user in an app.
 */
class AutomationActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)

        val action = actionFromExtra() ?: Hooks.actionFor(intent?.component?.className)

        if (action == null) {
            Toast.makeText(this, "That Yoinker action isn't set up yet.", Toast.LENGTH_SHORT).show()
        } else {
            Actions.perform(applicationContext, action)
            feedback(action)
        }

        finish()
    }

    /** App shortcuts and other apps name the action directly: --es action START_QUEUE. */
    private fun actionFromExtra(): Action? {
        val name = intent?.getStringExtra(EXTRA_ACTION)?.trim()?.uppercase() ?: return null
        val kind = ActionKind.entries.firstOrNull { it.name == name } ?: return null
        val modeId = intent?.getStringExtra(EXTRA_MODE).orEmpty()
        return Action(kind, modeId = modeId, text = intent?.getStringExtra(EXTRA_TEXT).orEmpty())
    }

    private fun feedback(action: Action) {
        // The clipboard action opens its own window; anything else is invisible
        // without a word about what just happened.
        if (action.kind == ActionKind.YOINK_CLIPBOARD) return
        val message = when (action.kind) {
            ActionKind.SET_MODE -> "Yoinker: ${Automation.modeById(action.modeId).name} mode"
            else -> "Yoinker: ${action.kind.label.lowercase()}"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_ACTION = "action"
        const val EXTRA_MODE = "mode"
        const val EXTRA_TEXT = "text"
    }
}
