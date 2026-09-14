package com.pylo.yoinker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoFixHigh
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import com.pylo.yoinker.convert.ConvertService
import com.pylo.yoinker.convert.ConvertState
import com.pylo.yoinker.core.formatBytes
import com.pylo.yoinker.core.formatDuration
import com.pylo.yoinker.core.formatEta
import com.pylo.yoinker.core.hostOf
import com.pylo.yoinker.download.Exporter
import com.pylo.yoinker.download.JobState
import com.pylo.yoinker.download.Queue
import com.pylo.yoinker.download.YoinkJob
import com.pylo.yoinker.download.YoinkService
import com.pylo.yoinker.engine.Converter
import com.pylo.yoinker.ui.theme.Space
import com.pylo.yoinker.ui.theme.Yk
import com.pylo.yoinker.ui.theme.asContent

@Composable
fun QueuePane() {
    val context = LocalContext.current
    val jobs by Queue.jobs.collectAsState()
    val paused by Queue.paused.collectAsState()
    val pending = jobs.count { !it.isFinished }

    Column(Modifier.fillMaxSize().padding(horizontal = Space.gutter)) {
        if (jobs.isEmpty()) {
            EmptyState(
                icon = Icons.Rounded.Inbox,
                title = "Nothing queued",
                body = "Share a link to Yoinker, or paste one yourself.",
            )
            return@Column
        }

        ScreenTitle(
            title = "Queue",
            subtitle = when {
                paused -> "Paused — nothing will start on its own."
                pending > 0 -> "$pending waiting to be yoinked."
                else -> "All done."
            },
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextAction(
                text = if (paused) "Resume" else "Pause",
                icon = if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
            ) {
                if (paused) {
                    Queue.setPaused(false)
                    if (Queue.pendingCount() > 0) YoinkService.kick(context.applicationContext)
                } else {
                    Queue.setPaused(true)
                    YoinkService.kick(context.applicationContext, YoinkService.ACTION_PAUSE)
                }
            }
            Spacer(Modifier.weight(1f))
            if (jobs.any { it.isFinished }) {
                TextAction("Clear finished", icon = Icons.Rounded.Delete, tint = Yk.InkFaint) {
                    Queue.clearFinished()
                }
            }
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Space.md),
            contentPadding = PaddingValues(top = Space.md, bottom = Space.xxl),
        ) {
            items(jobs.reversed(), key = { it.id }) { job ->
                JobCard(job = job, onOpen = { open(context, job) })
            }
        }
    }
}

@Composable
private fun JobCard(job: YoinkJob, onOpen: () -> Unit) {
    val context = LocalContext.current
    val running = job.state == JobState.RUNNING

    YkCard(
        brush = if (running) Yk.raisedCard else Yk.card,
        border = if (running) Yk.GoldDeep else Yk.Hairline,
        padding = Space.md,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(url = job.thumbnail, duration = formatDuration(job.durationSec))

            Column(Modifier.weight(1f).padding(start = Space.lg)) {
                Text(
                    text = job.label,
                    style = MaterialTheme.typography.titleMedium.asContent(),
                    color = Yk.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Space.xs))
                Text(
                    text = "${job.format.label}  ·  ${job.quality}  ·  ${hostOf(job.url).ifEmpty { "link" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Yk.InkFaint,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        if (running) {
            Spacer(Modifier.height(Space.lg))
            YkProgress(fraction = job.progress / 100f, indeterminate = job.progress <= 0f)
            Spacer(Modifier.height(Space.sm))
            Text(
                text = buildString {
                    append("${job.progress.toInt()}%")
                    formatEta(job.etaSeconds).takeIf { it.isNotEmpty() }?.let { append("   ·   $it") }
                },
                style = MaterialTheme.typography.labelMedium,
                color = Yk.InkSoft,
            )
        } else {
            Spacer(Modifier.height(Space.md))
            when (job.state) {
                JobState.QUEUED -> StatusChip("Waiting", Yk.InkFaint)
                JobState.DONE -> StatusChip(
                    "Saved" + (job.sizeBytes.takeIf { it > 0 }?.let { "  ·  ${formatBytes(it)}" } ?: ""),
                    Yk.Green,
                )
                JobState.FAILED -> StatusChip(job.error ?: "Failed", Yk.Ember)
                JobState.CANCELED -> StatusChip("Stopped", Yk.InkFaint)
                JobState.RUNNING -> Unit
            }
        }

        job.warning?.let {
            Spacer(Modifier.height(Space.sm))
            StatusChip(it, Yk.Ember)
        }

        Spacer(Modifier.height(Space.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
            when (job.state) {
                JobState.RUNNING -> TextAction("Stop", icon = Icons.Rounded.Stop) {
                    YoinkService.kick(context.applicationContext, YoinkService.ACTION_CANCEL_JOB, job.id)
                }

                JobState.QUEUED -> TextAction("Start now", icon = Icons.Rounded.PlayArrow) {
                    Queue.setPaused(false)
                    YoinkService.kick(context.applicationContext)
                }

                JobState.DONE -> TextAction("Open", icon = Icons.Rounded.OpenInNew, onClick = onOpen)

                else -> TextAction("Retry", icon = Icons.Rounded.Refresh) { Queue.retry(job.id) }
            }

            // A file that came out in a codec this phone won't draw can be
            // re-encoded in place rather than downloaded again.
            if (job.warning != null && job.savedUri != null) {
                TextAction("Make it playable", icon = Icons.Rounded.AutoFixHigh) {
                    val uri = Uri.parse(job.savedUri)
                    ConvertState.setSource(uri, job.savedName.orEmpty())
                    ConvertService.start(context, uri, Converter.Target.Mp4())
                    Toast.makeText(context, "Converting so it plays…", Toast.LENGTH_SHORT).show()
                }
            }

            if (!running) {
                TextAction("Remove", tint = Yk.InkFaint) { Queue.remove(job.id) }
            }
        }
    }
}

private fun open(context: Context, job: YoinkJob) {
    val uri = job.savedUri?.let(Uri::parse)
    if (uri == null) {
        Toast.makeText(context, "That file isn't on this phone any more.", Toast.LENGTH_SHORT).show()
        return
    }
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, Exporter.mimeFor(job.format))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "Nothing here opens that file.", Toast.LENGTH_SHORT).show() }
}
