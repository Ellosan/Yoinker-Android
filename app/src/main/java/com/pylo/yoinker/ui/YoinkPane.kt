package com.pylo.yoinker.ui

import android.content.ClipboardManager
import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
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
import com.pylo.yoinker.ui.theme.Yk
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

    val ready = extractUrl(url) != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 28.dp),
    ) {
        Text(
            text = "Paste a link,\nget a clean file.",
            style = MaterialTheme.typography.displaySmall,
            color = Yk.Ink,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "No sketchy converter sites. Runs on this phone.",
            style = MaterialTheme.typography.bodyMedium,
            color = Yk.InkFaint,
            modifier = Modifier.padding(top = 8.dp),
        )

        AnimatedVisibility(visible = engineState is YoinkEngine.State.Preparing || engineState is YoinkEngine.State.Failed) {
            Banner(
                text = when (val s = engineState) {
                    is YoinkEngine.State.Failed -> "⚠  ${s.message}"
                    else -> "Getting the download engine ready — first launch only."
                },
                error = engineState is YoinkEngine.State.Failed,
            )
        }

        SectionLabel("Link")
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = { Text("Paste a video or audio URL…", color = Yk.InkFaint) },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            textStyle = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Yk.GoldDeep,
                unfocusedBorderColor = Yk.Line,
                focusedContainerColor = Yk.PanelHigh,
                unfocusedContainerColor = Yk.PanelHigh,
                cursorColor = Yk.Gold,
                focusedTextColor = Yk.Ink,
                unfocusedTextColor = Yk.Ink,
            ),
            trailingIcon = {
                if (url.isNotEmpty()) {
                    TextAction("Clear", tint = Yk.InkFaint) { url = "" }
                } else {
                    TextAction("Paste") { url = clipboardText(context).orEmpty() }
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        )

        AnimatedVisibility(
            visible = peek !is PeekState.None,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            LinkPreview(
                url = extractUrl(url).orEmpty(),
                state = peek,
                modifier = Modifier.padding(top = 14.dp),
            )
        }

        SectionLabel("Format")
        FormatToggle(format = format) {
            format = it
            quality = Qualities.default(it)
        }

        SectionLabel("Quality")
        QualityPicker(format = format, quality = quality) { quality = it }

        Spacer(Modifier.height(26.dp))

        GoldButton(
            text = "⬇   Yoink it",
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (enqueue(context, url, format, quality, mode.id, start = true)) url = ""
        }

        Spacer(Modifier.height(10.dp))

        GhostButton(
            text = "Add to queue",
            enabled = ready,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (enqueue(context, url, format, quality, mode.id, start = false)) url = ""
        }

        Spacer(Modifier.height(30.dp))

        Text(
            text = "Everything runs on this phone. The only thing that leaves is the request " +
                "to the link you paste. For personal use — respect each site's terms and copyright.",
            style = MaterialTheme.typography.bodyMedium,
            color = Yk.InkFaint,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(top = 6.dp),
        ) {
            TextAction(if (updating) "Updating…" else "⟳  Update engine") {
                if (updating) return@TextAction
                updating = true
                engineNote = ""
                scope.launch {
                    val result = withContext(Dispatchers.IO) {
                        YoinkEngine.updateEngine(context.applicationContext)
                    }
                    engineNote = result.getOrElse { it.message ?: "Update failed." }
                    updating = false
                }
            }
            if (engineNote.isNotEmpty()) {
                Text(engineNote, style = MaterialTheme.typography.bodyMedium, color = Yk.InkFaint)
            }
        }
    }
}

@Composable
private fun Banner(text: String, error: Boolean) {
    YkCard(
        brush = if (error) Yk.emberFaint else Yk.card,
        border = if (error) Yk.Ember.copy(alpha = 0.5f) else Yk.Line,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 18.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = if (error) Yk.Ink else Yk.InkSoft,
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
