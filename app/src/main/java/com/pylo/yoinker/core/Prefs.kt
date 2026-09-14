package com.pylo.yoinker.core

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit

/**
 * Everything Yoinker remembers. One small preferences file — the modes, routines
 * and queue ride along as JSON blobs so there's no database to migrate.
 */
object Prefs {

    private const val FILE = "yoinker"
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (!::sp.isInitialized) {
            sp = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)
        }
    }

    val isReady: Boolean get() = ::sp.isInitialized

    var activeModeId: String
        get() = sp.getString("activeMode", null) ?: "standard"
        set(v) = sp.edit { putString("activeMode", v) }

    var queuePaused: Boolean
        get() = sp.getBoolean("queuePaused", false)
        set(v) = sp.edit { putBoolean("queuePaused", v) }

    /** Nightly keeps up with the sites that change weekly; stable lags and starts failing. */
    var engineChannel: String
        get() = sp.getString("engineChannel", "nightly") ?: "nightly"
        set(v) = sp.edit { putString("engineChannel", v) }

    var autoUpdateEngine: Boolean
        get() = sp.getBoolean("autoUpdateEngine", true)
        set(v) = sp.edit { putBoolean("autoUpdateEngine", v) }

    var lastEngineUpdate: Long
        get() = sp.getLong("lastEngineUpdate", 0L)
        set(v) = sp.edit { putLong("lastEngineUpdate", v) }

    /** Opt-in background watcher for the routine triggers the system won't wake us for. */
    var watchInBackground: Boolean
        get() = sp.getBoolean("watchInBackground", false)
        set(v) = sp.edit { putBoolean("watchInBackground", v) }

    /** The launcher entries Modes & Routines can "open" as actions. */
    var hooksEnabled: Boolean
        get() = sp.getBoolean("hooksEnabled", false)
        set(v) = sp.edit { putBoolean("hooksEnabled", v) }

    var modesJson: String?
        get() = sp.getString("modes", null)
        set(v) = sp.edit { putString("modes", v) }

    var routinesJson: String?
        get() = sp.getString("routines", null)
        set(v) = sp.edit { putString("routines", v) }

    var queueJson: String?
        get() = sp.getString("queue", null)
        set(v) = sp.edit { putString("queue", v) }

    /** Which three modes the ModeA/B/C launcher aliases switch to. */
    fun aliasMode(slot: Int): String? = sp.getString("aliasMode$slot", null)

    fun setAliasMode(slot: Int, modeId: String?) {
        sp.edit { putString("aliasMode$slot", modeId) }
    }
}
