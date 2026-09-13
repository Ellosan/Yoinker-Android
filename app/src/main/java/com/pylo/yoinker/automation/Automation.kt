package com.pylo.yoinker.automation

import android.content.Context
import com.pylo.yoinker.core.Prefs
import com.pylo.yoinker.core.Qualities
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.Json

/** Stores the modes and routines, and remembers which mode is in charge. */
object Automation {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _modes = MutableStateFlow(Defaults.modes)
    val modes: StateFlow<List<Mode>> = _modes.asStateFlow()

    private val _routines = MutableStateFlow(Defaults.routines)
    val routines: StateFlow<List<Routine>> = _routines.asStateFlow()

    private val _activeModeId = MutableStateFlow("standard")
    val activeModeId: StateFlow<String> = _activeModeId.asStateFlow()

    fun load() {
        Prefs.modesJson?.let { stored ->
            runCatching { json.decodeFromString<List<Mode>>(stored) }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { saved ->
                    // Built-ins can gain fields between versions; saved copies win for
                    // the ones the user has, and any new built-in is added.
                    val savedIds = saved.map { it.id }.toSet()
                    _modes.value = saved + Defaults.modes.filterNot { it.id in savedIds }
                }
        }
        Prefs.routinesJson?.let { stored ->
            runCatching { json.decodeFromString<List<Routine>>(stored) }
                .getOrNull()
                ?.let { saved ->
                    val savedIds = saved.map { it.id }.toSet()
                    _routines.value = saved + Defaults.routines.filterNot { it.id in savedIds }
                }
        }
        _activeModeId.value = Prefs.activeModeId.takeIf { id -> _modes.value.any { it.id == id } } ?: "standard"
    }

    fun activeMode(): Mode = modeById(_activeModeId.value)

    fun modeById(id: String?): Mode =
        _modes.value.firstOrNull { it.id == id }
            ?: _modes.value.firstOrNull { it.id == "standard" }
            ?: Defaults.modes.first()

    fun setActiveMode(id: String) {
        if (_modes.value.none { it.id == id }) return
        _activeModeId.value = id
        Prefs.activeModeId = id
    }

    fun upsertMode(mode: Mode) {
        val fixed = mode.copy(quality = Qualities.sanitize(mode.format, mode.quality))
        val existing = _modes.value.indexOfFirst { it.id == fixed.id }
        _modes.value = if (existing >= 0) {
            _modes.value.toMutableList().also { it[existing] = fixed }
        } else {
            _modes.value + fixed
        }
        persistModes()
    }

    fun deleteMode(id: String) {
        val mode = _modes.value.firstOrNull { it.id == id } ?: return
        if (mode.builtIn) return
        _modes.value = _modes.value.filterNot { it.id == id }
        if (_activeModeId.value == id) setActiveMode("standard")
        persistModes()
    }

    fun upsertRoutine(routine: Routine) {
        val existing = _routines.value.indexOfFirst { it.id == routine.id }
        _routines.value = if (existing >= 0) {
            _routines.value.toMutableList().also { it[existing] = routine }
        } else {
            _routines.value + routine
        }
        persistRoutines()
    }

    fun deleteRoutine(id: String) {
        _routines.value = _routines.value.filterNot { it.id == id }
        persistRoutines()
    }

    fun setRoutineEnabled(id: String, enabled: Boolean) {
        _routines.value = _routines.value.map { if (it.id == id) it.copy(enabled = enabled) else it }
        persistRoutines()
    }

    fun markRoutineRun(id: String) {
        _routines.value = _routines.value.map { if (it.id == id) it.copy(lastRunAt = System.currentTimeMillis()) else it }
        persistRoutines()
    }

    fun routinesFor(kind: TriggerKind): List<Routine> =
        _routines.value.filter { it.enabled && it.trigger.kind == kind }

    /** Keeps the alarms and the background watcher in step with what's enabled. */
    fun applySchedules(context: Context) {
        Alarms.rescheduleAll(context)
        AutomationService.syncWithPreference(context)
        AutomationJobService.syncWithRoutines(context)
    }

    private fun persistModes() {
        Prefs.modesJson = runCatching { json.encodeToString(_modes.value) }.getOrNull()
    }

    private fun persistRoutines() {
        Prefs.routinesJson = runCatching { json.encodeToString(_routines.value) }.getOrNull()
    }
}
