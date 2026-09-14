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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.pylo.yoinker.ui.theme.Yk

@Composable
fun QueuePane() {
    val context = LocalContext.current
    val jobs by Queue.jobs.collectAsState()
    val paused by Queue.paused.collectAsState()

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        if (jobs.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextAction(if (paused) "▶   Resume queue" else "⏸   Pause queue") {
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
                    TextAction("Clear finished", tint = Yk.InkFaint) { Queue.clearFinished() }
                }
            }
        }

        if (jobs.isEmpty()) {
            EmptyState(
                mark = "🪝",
                title = "Nothing queued",
                body = "Share a link to Yoinker, or paste one on the Yoink tab.",
            )
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
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
        border = if (running) Yk.GoldDeep else Yk.Line,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Thumb(
                url = job.thumbnail,
                duration = formatDuration(job.durationSec),
            )
            Spacer(Modifier.height(0.dp))
            Column(Modifier.weight(1f).padding(start = 14.dp)) {
                Text(
                    text = job.label,
                    style = MaterialTheme.typography.titleMedium,
                    color = Yk.Ink,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "${job.format.label} · ${job.quality} · ${hostOf(job.url).ifEmpty { "link" }}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Yk.InkFaint,
                )
            }
        }

        if (running) {
            Spacer(Modifier.height(14.dp))
            YkProgress(fraction = job.progress / 100f, indeterminate = job.progress <= 0f)
            Text(
                text = buildString {
                    append("${job.progress.toInt()}%")
                    formatEta(job.etaSeconds).takeIf { it.isNotEmpty() }?.let { append("  ·  $it") }
                },
                style = MaterialTheme.typography.bodyMedium,
                color = Yk.InkSoft,
                modifier = Modifier.padding(top = 7.dp),
            )
        }

        when (job.state) {
            JobState.QUEUED -> StatusLine("Waiting", Yk.InkFaint)
            JobState.DONE -> StatusLine(
                "✓  Saved" + (job.sizeBytes.takeIf { it > 0 }?.let { "  ·  ${formatBytes(it)}" } ?: ""),
                Yk.Green,
            )
            JobState.FAILED -> StatusLine("⚠  ${job.error ?: "Failed"}", Yk.Ember)
            JobState.CANCELED -> StatusLine("Stopped", Yk.InkFaint)
            JobState.RUNNING -> Unit
        }

        job.warning?.let { StatusLine("⚠  $it", Yk.Ember) }

        Spacer(Modifier.height(6.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            when (job.state) {
                JobState.RUNNING -> TextAction("Stop") {
                    YoinkService.kick(context.applicationContext, YoinkService.ACTION_CANCEL_JOB, job.id)
                }

                JobState.QUEUED -> TextAction("Start now") {
                    Queue.setPaused(false)
                    YoinkService.kick(context.applicationContext)
                }

                JobState.DONE -> TextAction("Open", onClick = onOpen)

                else -> TextAction("Retry") { Queue.retry(job.id) }
            }

            // A file that came out in a codec this phone won't draw can be
            // re-encoded in place rather than downloaded again.
            if (job.warning != null && job.savedUri != null) {
                TextAction("Make it playable") {
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
