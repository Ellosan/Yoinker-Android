package com.pylo.yoinker.convert

import android.net.Uri
import com.pylo.yoinker.engine.Converter
import com.pylo.yoinker.engine.MediaProbe
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Where the Convert screen and [ConvertService] meet. One job at a time. */
object ConvertState {

    sealed interface Phase {
        data object Idle : Phase
        data object Reading : Phase
        data class Ready(val probe: MediaProbe) : Phase
        data class Working(val percent: Float) : Phase
        data class Done(val displayName: String, val savedUri: String?) : Phase
        data class Failed(val message: String) : Phase
    }

    private val _source = MutableStateFlow<Uri?>(null)
    val source: StateFlow<Uri?> = _source.asStateFlow()

    private val _sourceName = MutableStateFlow("")
    val sourceName: StateFlow<String> = _sourceName.asStateFlow()

    private val _phase = MutableStateFlow<Phase>(Phase.Idle)
    val phase: StateFlow<Phase> = _phase.asStateFlow()

    private val _target = MutableStateFlow<Converter.Target>(Converter.Target.Mp4())
    val target: StateFlow<Converter.Target> = _target.asStateFlow()

    fun setSource(uri: Uri?, name: String) {
        _source.value = uri
        _sourceName.value = name
        _phase.value = if (uri == null) Phase.Idle else Phase.Reading
    }

    fun setTarget(target: Converter.Target) {
        _target.value = target
    }

    fun set(phase: Phase) {
        _phase.value = phase
    }

    fun clear() {
        _source.value = null
        _sourceName.value = ""
        _phase.value = Phase.Idle
    }

    val isBusy: Boolean get() = _phase.value is Phase.Working
}
