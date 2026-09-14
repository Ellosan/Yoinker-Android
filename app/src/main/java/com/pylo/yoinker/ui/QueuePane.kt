package com.pylo.yoinker.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pylo.yoinker.core.formatBytes
import com.pylo.yoinker.core.formatEta
import com.pylo.yoinker.core.hostOf
import com.pylo.yoinker.download.Exporter
import com.pylo.yoinker.download.JobState
import com.pylo.yoinker.download.Queue
import com.pylo.yoinker.download.YoinkJob
import com.pylo.yoinker.download.YoinkService
import com.pylo.yoinker.ui.theme.YkEmber
import com.pylo.yoinker.ui.theme.YkGold
import com.pylo.yoinker.ui.theme.YkGreen
import com.pylo.yoinker.ui.theme.YkInkFaint

@Composable
fun QueuePane() {
    val context = LocalContext.current
    val jobs by Queue.jobs.collectAsState()
    val paused by Queue.paused.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = {
                if (paused) {
                    Queue.setPaused(false)
                    if (Queue.pendingCount() > 0) YoinkService.kick(context.applicationContext)
                } else {
                    Queue.setPaused(true)
                    YoinkService.kick(context.applicationContext, YoinkService.ACTION_PAUSE)
                }
            }) {
                Text(if (paused) "▶  Resume queue" else "⏸  Pause queue", color = YkGold)
            }

            Spacer(Modifier.weight(1f))

            if (jobs.any { it.isFinished }) {
                TextButton(onClick = { Queue.clearFinished() }) {
                    Text("Clear finished", color = YkInkFaint)
                }
            }
        }

        if (jobs.isEmpty()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Nothing queued", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Share a link to Yoinker, or paste one on the Yoink tab.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YkInkFaint,
                )
            }
            return@Column
        }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
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

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = job.label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${job.format.label} · ${job.quality} · ${hostOf(job.url).ifEmpty { "link" }}",
                style = MaterialTheme.typography.bodyMedium,
                color = YkInkFaint,
            )

            if (job.state == JobState.RUNNING) {
                Spacer(Modifier.height(10.dp))
                LinearProgressIndicator(
                    progress = { (job.progress / 100f).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = buildString {
                        append("${job.progress.toInt()}%")
                        formatEta(job.etaSeconds).takeIf { it.isNotEmpty() }?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = YkInkFaint,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            val status = when (job.state) {
                JobState.QUEUED -> "Waiting"
                JobState.RUNNING -> null
                JobState.DONE -> "✓ Saved${job.sizeBytes.takeIf { it > 0 }?.let { " · ${formatBytes(it)}" } ?: ""}" +
                    (job.warning?.let { "\n⚠ $it" } ?: "")
                JobState.FAILED -> "⚠ ${job.error ?: "Failed"}"
                JobState.CANCELED -> "Stopped"
            }
            if (status != null) {
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (job.state) {
                        JobState.DONE -> YkGreen
                        JobState.FAILED -> YkEmber
                        else -> YkInkFaint
                    },
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                when (job.state) {
                    JobState.RUNNING -> TextButton(onClick = {
                        YoinkService.kick(
                            context.applicationContext,
                            YoinkService.ACTION_CANCEL_JOB,
                            job.id,
                        )
                    }) { Text("Stop", color = YkGold) }

                    JobState.QUEUED -> TextButton(onClick = {
                        Queue.setPaused(false)
                        YoinkService.kick(context.applicationContext)
                    }) { Text("Start now", color = YkGold) }

                    JobState.DONE -> TextButton(onClick = onOpen) { Text("Open", color = YkGold) }

                    else -> TextButton(onClick = { Queue.retry(job.id) }) { Text("Retry", color = YkGold) }
                }

                if (job.state != JobState.RUNNING) {
                    TextButton(onClick = { Queue.remove(job.id) }) {
                        Text("Remove", color = YkInkFaint)
                    }
                }
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
