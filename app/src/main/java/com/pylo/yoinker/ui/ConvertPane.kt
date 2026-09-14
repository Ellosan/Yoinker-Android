package com.pylo.yoinker.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.pylo.yoinker.ui.theme.Yk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MP3_BITRATES = listOf("320K" to "320 kbps", "192K" to "192 kbps", "128K" to "128 kbps")
private val MP4_HEIGHTS = listOf(0 to "Original", 1080 to "1080p", 720 to "720p", 480 to "480p")

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

    val working = phase is ConvertState.Phase.Working

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(
            text = "Convert\nwhat you have.",
            style = MaterialTheme.typography.displaySmall,
            color = Yk.Ink,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "Pull the audio out as an MP3, or turn a video the phone won't play " +
                "into one it will. Nothing is uploaded.",
            style = MaterialTheme.typography.bodyMedium,
            color = Yk.InkFaint,
            modifier = Modifier.padding(top = 8.dp),
        )

        SectionLabel("File")
        GhostButton(
            text = info?.name?.takeIf { it.isNotBlank() }?.take(38) ?: "Choose a video or audio file…",
            enabled = !working,
            tint = if (info == null) Yk.InkSoft else Yk.GoldBright,
            modifier = Modifier.fillMaxWidth(),
        ) {
            picker.launch(arrayOf("video/*", "audio/*"))
        }

        AnimatedVisibility(visible = info != null) {
            info?.let { SourceCard(it) }
        }

        if (info != null) {
            SectionLabel("Convert to")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Pill(
                    label = "🎬   Playable MP4",
                    selected = target is Converter.Target.Mp4,
                    enabled = info?.hasVideo == true && !working,
                    onClick = { ConvertState.setTarget(Converter.Target.Mp4()) },
                    modifier = Modifier.weight(1f),
                )
                Pill(
                    label = "🎵   MP3",
                    selected = target is Converter.Target.Mp3,
                    enabled = !working,
                    onClick = { ConvertState.setTarget(Converter.Target.Mp3()) },
                    modifier = Modifier.weight(1f),
                )
            }

            SectionLabel(if (target is Converter.Target.Mp3) "Bitrate" else "Size")
            Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                when (val t = target) {
                    is Converter.Target.Mp3 -> MP3_BITRATES.forEach { (value, label) ->
                        Pill(
                            label = label,
                            selected = t.bitrate == value,
                            enabled = !working,
                            onClick = { ConvertState.setTarget(Converter.Target.Mp3(value)) },
                        )
                    }

                    is Converter.Target.Mp4 -> MP4_HEIGHTS.forEach { (value, label) ->
                        Pill(
                            label = label,
                            selected = t.maxHeight == value,
                            enabled = !working,
                            onClick = { ConvertState.setTarget(Converter.Target.Mp4(value)) },
                        )
                    }
                }
            }

            if (target is Converter.Target.Mp4 && info?.looksPlayable == true) {
                Text(
                    text = "This one already plays — converting is only worth it to shrink it.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Yk.InkFaint,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        Spacer(Modifier.height(26.dp))

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
    val broken = info.hasVideo && !info.looksPlayable

    YkCard(
        brush = if (broken) Yk.emberFaint else Yk.card,
        border = if (broken) Yk.Ember.copy(alpha = 0.5f) else Yk.Line,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
    ) {
        Text(
            text = info.name,
            style = MaterialTheme.typography.titleMedium,
            color = Yk.Ink,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = buildList {
                info.videoCodecName?.let { add(it) }
                if (info.width > 0) add("${info.width}×${info.height}")
                formatDuration(info.durationSec.toInt()).takeIf { it.isNotEmpty() }?.let { add(it) }
                formatBytes(info.sizeBytes).takeIf { it.isNotEmpty() }?.let { add(it) }
            }.joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = Yk.InkFaint,
        )

        if (broken) {
            Text(
                text = "⚠  ${info.videoCodecName} video — the kind most players show as a " +
                    "blank screen. Converting to MP4 fixes it.",
                style = MaterialTheme.typography.bodyMedium,
                color = Yk.Ink,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun PhaseView(phase: ConvertState.Phase, canStart: Boolean, onStart: () -> Unit) {
    val context = LocalContext.current

    when (phase) {
        is ConvertState.Phase.Working -> {
            YkProgress(fraction = phase.percent / 100f, indeterminate = phase.percent <= 0f)
            Text(
                text = if (phase.percent <= 0f) "Starting…" else "Converting…  ${phase.percent.toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = Yk.InkSoft,
                modifier = Modifier.padding(top = 9.dp),
            )
            Spacer(Modifier.height(8.dp))
            TextAction("Stop") { ConvertService.stop(context) }
        }

        is ConvertState.Phase.Done -> {
            YkCard(modifier = Modifier.fillMaxWidth()) {
                Text("✓  Saved", style = MaterialTheme.typography.titleMedium, color = Yk.Green)
                Spacer(Modifier.height(3.dp))
                Text(
                    text = phase.displayName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Yk.InkFaint,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    phase.savedUri?.let { saved ->
                        TextAction("Open") { open(context, saved, phase.displayName) }
                    }
                    TextAction("Convert another", tint = Yk.InkFaint) { ConvertState.clear() }
                }
            }
        }

        is ConvertState.Phase.Failed -> {
            YkCard(
                brush = Yk.emberFaint,
                border = Yk.Ember.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("⚠  ${phase.message}", style = MaterialTheme.typography.bodyMedium, color = Yk.Ink)
            }
            Spacer(Modifier.height(12.dp))
            GoldButton("Try again", enabled = canStart, modifier = Modifier.fillMaxWidth(), onClick = onStart)
        }

        else -> GoldButton(
            text = "⚙   Convert",
            enabled = canStart,
            modifier = Modifier.fillMaxWidth(),
            onClick = onStart,
        )
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
