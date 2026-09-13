package com.pylo.yoinker.automation

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.pylo.yoinker.R
import com.pylo.yoinker.core.Prefs
import com.pylo.yoinker.share.ShareActivity

/**
 * The seams other apps reach Yoinker through.
 *
 * Samsung's Modes & Routines has no public plugin API — its "Then" step can open an
 * app, but it can't hand one a custom intent. So each Yoinker action is published as
 * its own launchable entry: Modes & Routines (and Bixby, and any launcher) sees
 * "Yoinker: start queue" as something openable, and opening it runs that one action
 * and closes again. They stay switched off until asked for, so the app drawer isn't
 * six icons heavier for someone who never automates anything.
 */
object Hooks {

    const val SHARE_CATEGORY = "com.pylo.yoinker.category.YOINK_TARGET"

    data class Alias(
        val className: String,
        val kind: ActionKind,
        /** Which of the three user-assignable mode slots this alias switches to. */
        val modeSlot: Int = 0,
        val label: String,
    )

    val aliases = listOf(
        Alias("com.pylo.yoinker.automation.alias.YoinkClipboard", ActionKind.YOINK_CLIPBOARD, label = "Yoink clipboard"),
        Alias("com.pylo.yoinker.automation.alias.StartQueue", ActionKind.START_QUEUE, label = "Start queue"),
        Alias("com.pylo.yoinker.automation.alias.PauseQueue", ActionKind.PAUSE_QUEUE, label = "Pause queue"),
        Alias("com.pylo.yoinker.automation.alias.ModeA", ActionKind.SET_MODE, modeSlot = 1, label = "Switch to mode 1"),
        Alias("com.pylo.yoinker.automation.alias.ModeB", ActionKind.SET_MODE, modeSlot = 2, label = "Switch to mode 2"),
        Alias("com.pylo.yoinker.automation.alias.ModeC", ActionKind.SET_MODE, modeSlot = 3, label = "Switch to mode 3"),
    )

    fun setEnabled(context: Context, enabled: Boolean) {
        Prefs.hooksEnabled = enabled
        val state = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        aliases.forEach { alias ->
            runCatching {
                context.packageManager.setComponentEnabledSetting(
                    ComponentName(context.packageName, alias.className),
                    state,
                    PackageManager.DONT_KILL_APP,
                )
            }
        }
    }

    /** What an alias should do, given which one was opened. */
    fun actionFor(className: String?): Action? {
        val alias = aliases.firstOrNull { it.className == className } ?: return null
        return when (alias.kind) {
            ActionKind.SET_MODE -> {
                val modeId = Prefs.aliasMode(alias.modeSlot) ?: return null
                Action(ActionKind.SET_MODE, modeId = modeId)
            }
            else -> Action(alias.kind)
        }
    }

    /** The mode an alias slot is pointed at, for the Routines screen to show. */
    fun modeForSlot(slot: Int): Mode? =
        Prefs.aliasMode(slot)?.let { id -> Automation.modes.value.firstOrNull { it.id == id } }

    fun assignSlot(context: Context, slot: Int, modeId: String?) {
        Prefs.setAliasMode(slot, modeId)
        if (Prefs.hooksEnabled) setEnabled(context, true)
    }

    /**
     * Puts "Yoink · Music", "Yoink · Video" straight onto the share sheet's top row,
     * so sharing a link is one tap rather than three.
     */
    fun refreshShareTargets(context: Context) {
        runCatching {
            val limit = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).coerceAtLeast(4)
            val modes = Automation.modes.value.take((limit - 2).coerceIn(1, 4))

            val targets = modes.map { mode ->
                val intent = Intent(context, ShareActivity::class.java)
                    .setAction(Intent.ACTION_SEND)
                    .setType("text/plain")
                    .putExtra(ShareActivity.EXTRA_MODE_ID, mode.id)

                ShortcutInfoCompat.Builder(context, "mode:${mode.id}")
                    .setShortLabel("Yoink · ${mode.name}")
                    .setLongLabel("Yoink as ${mode.format.label} (${mode.name})")
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_yoink))
                    .setCategories(setOf(SHARE_CATEGORY))
                    .setLongLived(true)
                    .setIntent(intent)
                    .build()
            }
            ShortcutManagerCompat.setDynamicShortcuts(context, targets)
        }
    }
}
