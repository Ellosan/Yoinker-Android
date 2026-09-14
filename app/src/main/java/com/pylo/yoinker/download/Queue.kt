package com.pylo.yoinker.download

import com.pylo.yoinker.core.Fmt
import com.pylo.yoinker.core.Prefs
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID

enum class JobState { QUEUED, RUNNING, DONE, FAILED, CANCELED }

@Serializable
data class YoinkJob(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val format: Fmt,
    val quality: String,
    val modeId: String = "standard",
    val title: String? = null,
    val uploader: String? = null,
    val thumbnail: String? = null,
    val durationSec: Int = 0,
    val state: JobState = JobState.QUEUED,
    val progress: Float = 0f,
    val etaSeconds: Long = -1L,
    val savedUri: String? = null,
    val savedName: String? = null,
    val sizeBytes: Long = 0L,
    val error: String? = null,
    /** Saved fine, but something about the file is worth knowing — see Mp4Probe. */
    val warning: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val label: String get() = title ?: savedName ?: url
    val isFinished: Boolean get() = state == JobState.DONE || state == JobState.FAILED || state == JobState.CANCELED
}

/**
 * The queue. Everything that wants a file yoinked — the share sheet, a routine,
 * the Yoink button — puts a job in here; [YoinkService] is the only thing that
 * takes them out.
 */
object Queue {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _jobs = MutableStateFlow<List<YoinkJob>>(emptyList())
    val jobs: StateFlow<List<YoinkJob>> = _jobs.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    fun load() {
        _paused.value = Prefs.queuePaused
        val stored = Prefs.queueJson ?: return
        val restored = runCatching { json.decodeFromString<List<YoinkJob>>(stored) }.getOrNull() ?: return
        // A job that was mid-flight when the process died is not running any more.
        _jobs.value = restored.map { if (it.state == JobState.RUNNING) it.copy(state = JobState.QUEUED, progress = 0f) else it }
    }

    fun add(job: YoinkJob): YoinkJob {
        _jobs.value = _jobs.value + job
        persist()
        return job
    }

    fun update(id: String, transform: (YoinkJob) -> YoinkJob) {
        var changedState = false
        _jobs.value = _jobs.value.map { job ->
            if (job.id != id) job else transform(job).also { changedState = it.state != job.state }
        }
        // Progress ticks every few hundred milliseconds; only milestones are worth writing.
        if (changedState) persist()
    }

    fun get(id: String): YoinkJob? = _jobs.value.firstOrNull { it.id == id }

    fun nextQueued(): YoinkJob? = _jobs.value.firstOrNull { it.state == JobState.QUEUED }

    fun running(): YoinkJob? = _jobs.value.firstOrNull { it.state == JobState.RUNNING }

    fun pendingCount(): Int = _jobs.value.count { it.state == JobState.QUEUED || it.state == JobState.RUNNING }

    fun remove(id: String) {
        _jobs.value = _jobs.value.filterNot { it.id == id }
        persist()
    }

    fun clearFinished() {
        _jobs.value = _jobs.value.filterNot { it.isFinished }
        persist()
    }

    fun retry(id: String) {
        update(id) { it.copy(state = JobState.QUEUED, progress = 0f, error = null, etaSeconds = -1L) }
    }

    fun setPaused(value: Boolean) {
        _paused.value = value
        Prefs.queuePaused = value
    }

    private fun persist() {
        if (!Prefs.isReady) return
        // Keep the tail short: the queue is a worklist, not a library.
        val trimmed = _jobs.value.takeLast(60)
        Prefs.queueJson = runCatching { json.encodeToString(trimmed) }.getOrNull()
    }
}
