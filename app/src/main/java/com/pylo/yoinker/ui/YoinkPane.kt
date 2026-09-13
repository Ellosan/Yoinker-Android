package com.pylo.yoinker.ui

import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pylo.yoinker.automation.Automation
import com.pylo.yoinker.automation.RoutineEngine
import com.pylo.yoinker.automation.TriggerKind
import com.pylo.yoinker.core.Fmt
import com.pylo.yoinker.core.Qualities
import com.pylo.yoinker.core.extractUrl
import com.pylo.yoinker.download.Queue
import com.pylo.yoinker.download.YoinkJob
import com.pylo.yoinker.download.YoinkService
import com.pylo.yoinker.engine.YoinkEngine
import com.pylo.yoinker.ui.theme.YkEmber
import com.pylo.yoinker.ui.theme.YkGold
import com.pylo.yoinker.ui.theme.YkInkFaint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun YoinkPane() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val engineState by YoinkEngine.state.collectAsState()
    val activeModeId by Automation.activeModeId.collectAsState()
    val mode = Automation.modeById(activeModeId)

    var url by remember { mutableStateOf("") }
    var format by remember { mutableStateOf(mode.format) }
    var quality by remember { mutableStateOf(mode.quality) }
    var peek by remember { mutableStateOf<PeekState>(PeekState.None) }
    var engineNote by remember { mutableStateOf("") }
    var updating by remember { mutableStateOf(false) }

    // Follow the mode until the user overrides it by hand for this one download.
    LaunchedEffect(activeModeId) {
        format = mode.format
        quality = mode.quality
    }

    // Reading a link costs a network round trip, so wait for typing to settle.
    LaunchedEffect(url) {
        val clean = extractUrl(url)
        if (clean == null) {
            peek = PeekState.None
            return@LaunchedEffect
        }
        delay(600)
        peek = PeekState.Loading
        val result = withContext(Dispatchers.IO) { YoinkEngine.peek(context.applicationContext, clean) }
        peek = result.fold(
            onSuccess = { PeekState.Loaded(it) },
            onFailure = { PeekState.Failed(it.message ?: "Couldn't read that link.") },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 24.dp),
    ) {
        when (val state = engineState) {
            is YoinkEngine.State.Preparing -> Banner("Getting the download engine ready — first launch only.")
            is YoinkEngine.State.Failed -> Banner("⚠ ${state.message}", error = true)
            else -> Unit
        }

        SectionLabel("Link")
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = { Text("Paste a video or audio URL…") },
            singleLine = true,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (url.isNotEmpty()) {
                    TextButton(onClick = { url = "" }) { Text("✕") }
                } else {
                    TextButton(onClick = { url = clipboardText(context).orEmpty() }) { Text("Paste") }
                }
            },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Done),
        )

        Spacer(Modifier.height(12.dp))
        LinkPreview(url = extractUrl(url).orEmpty(), state = peek)

        SectionLabel("Format")
        FormatToggle(format = format) {
            format = it
            quality = Qualities.default(it)
        }

        SectionLabel("Quality")
        QualityPicker(format = format, quality = quality) { quality = it }

        Spacer(Modifier.height(20.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    if (enqueue(context, url, format, quality, mode.id, start = false)) url = ""
                },
                enabled = extractUrl(url) != null,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1f),
            ) { Text("Queue it") }

            Button(
                onClick = {
                    if (enqueue(context, url, format, quality, mode.id, start = true)) url = ""
                },
                enabled = extractUrl(url) != null,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.weight(1.4f),
            ) { Text("⬇  Yoink") }
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Everything runs on this phone. The only thing that leaves is the request " +
                "to the link you paste. For personal use — respect each site's terms and copyright.",
            style = MaterialTheme.typography.bodyMedium,
            color = YkInkFaint,
        )

        Spacer(Modifier.height(8.dp))

        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            TextButton(
                enabled = !updating,
                onClick = {
                    updating = true
                    engineNote = "Updating…"
                    scope.launch {
                        val result = withContext(Dispatchers.IO) {
                            YoinkEngine.updateEngine(context.applicationContext)
                        }
                        engineNote = result.getOrElse { it.message ?: "Update failed." }
                        updating = false
                    }
                },
            ) { Text("⟳ Update engine", color = YkGold) }

            if (engineNote.isNotEmpty()) {
                Text(
                    text = engineNote,
                    style = MaterialTheme.typography.bodyMedium,
                    color = YkInkFaint,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Banner(text: String, error: Boolean = false) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (error) YkEmber.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceVariant,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(12.dp),
        )
    }
}

private fun enqueue(
    context: Context,
    rawUrl: String,
    format: Fmt,
    quality: String,
    modeId: String,
    start: Boolean,
): Boolean {
    val url = extractUrl(rawUrl) ?: return false
    Queue.add(
        YoinkJob(
            url = url,
            format = format,
            quality = Qualities.sanitize(format, quality),
            modeId = modeId,
        )
    )
    RoutineEngine.fire(context.applicationContext, TriggerKind.LINK_SHARED)
    if (start && !Queue.paused.value) YoinkService.kick(context.applicationContext)
    return true
}

private fun clipboardText(context: Context): String? {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    return runCatching { clip.getItemAt(0).coerceToText(context).toString() }.getOrNull()
}
