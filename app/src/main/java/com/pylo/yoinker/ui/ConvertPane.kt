package com.pylo.yoinker.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pylo.yoinker.convert.ConvertService
import com.pylo.yoinker.convert.ConvertState
import com.pylo.yoinker.convert.SourceInfo
import com.pylo.yoinker.core.Fmt
import com.pylo.yoinker.core.formatBytes
import com.pylo.yoinker.core.formatDuration
import com.pylo.yoinker.download.Exporter
import com.pylo.yoinker.engine.Converter
import com.pylo.yoinker.ui.theme.YkEmber
import com.pylo.yoinker.ui.theme.YkGold
import com.pylo.yoinker.ui.theme.YkGreen
import com.pylo.yoinker.ui.theme.YkInkFaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MP3_BITRATES = listOf("320K" to "320 kbps", "192K" to "192 kbps", "128K" to "128 kbps")
private val MP4_HEIGHTS = listOf(0 to "Original size", 1080 to "1080p", 720 to "720p", 480 to "480p")

@Composable
fun ConvertPane() {
    val context = LocalContext.current
    val source by ConvertState.source.collectAsState()
    val phase by ConvertState.phase.collectAsState()
    val target by ConvertState.target.collectAsState()

    var info by remember { mutableStateOf<SourceInfo?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            // The conversion runs in a service, after this screen may be gone, so
            // hold the read grant rather than relying on the activity's own.
            runCatching {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ConvertState.setSource(uri, "")
        }
    }

    LaunchedEffect(source) {
        val uri = source
        info = if (uri == null) null else withContext(Dispatchers.IO) { SourceInfo.read(context, uri) }
        if (uri != null && info != null && phase !is ConvertState.Phase.Working) {
            ConvertState.setSource(uri, info?.name.orEmpty())
            ConvertState.set(ConvertState.Phase.Idle)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        Text(
            text = "Turn a file you already have into an MP3, or into video the phone " +
                "will actually play. Runs on this device — nothing is uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = YkInkFaint,
            modifier = Modifier.padding(top = 8.dp),
        )

        SectionLabel("File")
        OutlinedButton(
            onClick = { picker.launch(arrayOf("video/*", "audio/*")) },
            enabled = phase !is ConvertState.Phase.Working,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(info?.name?.takeIf { it.isNotBlank() } ?: "Choose a video or audio file…")
        }

        info?.let { SourceCard(it) }

        if (info != null) {
            SectionLabel("Convert to")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                FilterChip(
                    selected = target is Converter.Target.Mp4,
                    onClick = { ConvertState.setTarget(Converter.Target.Mp4()) },
                    label = { Text("🎬  Playable MP4") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                    enabled = info?.hasVideo == true,
                )
                FilterChip(
                    selected = target is Converter.Target.Mp3,
                    onClick = { ConvertState.setTarget(Converter.Target.Mp3()) },
                    label = { Text("🎵  MP3") },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                )
            }

            SectionLabel(if (target is Converter.Target.Mp3) "Bitrate" else "Size")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (val t = target) {
                    is Converter.Target.Mp3 -> MP3_BITRATES.forEach { (value, label) ->
                        FilterChip(
                            selected = t.bitrate == value,
                            onClick = { ConvertState.setTarget(Converter.Target.Mp3(value)) },
                            label = { Text(label) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }

                    is Converter.Target.Mp4 -> MP4_HEIGHTS.forEach { (value, label) ->
                        FilterChip(
                            selected = t.maxHeight == value,
                            onClick = { ConvertState.setTarget(Converter.Target.Mp4(value)) },
                            label = { Text(label) },
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }
            }

            if (target is Converter.Target.Mp4 && info?.looksPlayable == true) {
                Text(
                    text = "This one already plays. Converting is only worth it to shrink it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YkInkFaint,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        PhaseView(phase = phase, canStart = info != null) {
            val uri = source ?: return@PhaseView
            ConvertState.set(ConvertState.Phase.Working(0f))
            if (!ConvertService.start(context, uri, target)) {
                ConvertState.set(ConvertState.Phase.Failed("Android wouldn't let the converter start just now."))
            }
        }
    }
}

@Composable
private fun SourceCard(info: SourceInfo) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(
                text = info.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val bits = buildList {
                info.videoCodecName?.let { add(it) }
                if (info.width > 0) add("${info.width}×${info.height}")
                formatDuration(info.durationSec.toInt()).takeIf { it.isNotEmpty() }?.let { add(it) }
                formatBytes(info.sizeBytes).takeIf { it.isNotEmpty() }?.let { add(it) }
            }
            Text(bits.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = YkInkFaint)

            if (info.hasVideo && !info.looksPlayable) {
                Text(
                    text = "⚠ ${info.videoCodecName} video — this is the kind most players " +
                        "show as a blank screen. Converting to MP4 fixes it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YkEmber,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun PhaseView(phase: ConvertState.Phase, canStart: Boolean, onStart: () -> Unit) {
    val context = LocalContext.current

    when (phase) {
        is ConvertState.Phase.Working -> {
            LinearProgressIndicator(
                progress = { (phase.percent / 100f).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = if (phase.percent <= 0f) "Starting…" else "Converting… ${phase.percent.toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = YkInkFaint,
                modifier = Modifier.padding(top = 8.dp),
            )
            TextButton(onClick = { ConvertService.stop(context) }) { Text("Stop", color = YkGold) }
        }

        is ConvertState.Phase.Done -> {
            Text("✓ Saved ${phase.displayName}", style = MaterialTheme.typography.titleMedium, color = YkGreen)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                phase.savedUri?.let { saved ->
                    TextButton(onClick = { open(context, saved, phase.displayName) }) {
                        Text("Open", color = YkGold)
                    }
                }
                TextButton(onClick = { ConvertState.clear() }) { Text("Convert another", color = YkInkFaint) }
            }
        }

        is ConvertState.Phase.Failed -> {
            Text("⚠ ${phase.message}", style = MaterialTheme.typography.bodyMedium, color = YkEmber)
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = onStart,
                enabled = canStart,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Try again") }
        }

        else -> Button(
            onClick = onStart,
            enabled = canStart,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(),
        ) { Text("⚙  Convert") }
    }
}

private fun open(context: android.content.Context, uri: String, name: String) {
    val fmt = if (name.endsWith(".mp3", true)) Fmt.MP3 else Fmt.MP4
    val intent = Intent(Intent.ACTION_VIEW)
        .setDataAndType(Uri.parse(uri), Exporter.mimeFor(fmt))
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { context.startActivity(intent) }
        .onFailure { Toast.makeText(context, "Nothing here opens that file.", Toast.LENGTH_SHORT).show() }
}
