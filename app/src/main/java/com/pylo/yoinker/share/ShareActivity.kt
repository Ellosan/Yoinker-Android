package com.pylo.yoinker.share

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pylo.yoinker.automation.Automation
import com.pylo.yoinker.automation.Mode
import com.pylo.yoinker.automation.RoutineEngine
import com.pylo.yoinker.automation.TriggerKind
import com.pylo.yoinker.core.Fmt
import com.pylo.yoinker.core.Prefs
import com.pylo.yoinker.core.Qualities
import com.pylo.yoinker.core.extractUrl
import com.pylo.yoinker.download.Queue
import com.pylo.yoinker.download.YoinkJob
import com.pylo.yoinker.download.YoinkService
import com.pylo.yoinker.engine.YoinkEngine
import com.pylo.yoinker.ui.LinkPreview
import com.pylo.yoinker.ui.PeekState
import com.pylo.yoinker.ui.FormatToggle
import com.pylo.yoinker.ui.QualityPicker
import com.pylo.yoinker.ui.SectionLabel
import com.pylo.yoinker.ui.theme.YkInkFaint
import com.pylo.yoinker.ui.theme.YoinkerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * "Yoink" in the share sheet.
 *
 * Share a link from anywhere — a browser, a chat, a video app — and this is what
 * opens: the link already filled in, a preview of what it is, and one button. In a
 * mode with instant sharing on, there's no sheet at all; the download just starts.
 */
class ShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        val mode = resolveMode(intent)
        val url = extractUrl(incomingText(intent))

        if (url == null) {
            Toast.makeText(this, "No link in there to yoink.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        if (mode.instantShare) {
            enqueue(url, mode, mode.format, mode.quality, startNow = true, title = null)
            Toast.makeText(this, "Yoinking as ${mode.format.label}…", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            YoinkerTheme {
                ShareSheet(
                    url = url,
                    mode = mode,
                    onDismiss = { finish() },
                    onYoink = { fmt, quality, startNow, title ->
                        enqueue(url, mode, fmt, quality, startNow, title)
                        Toast.makeText(
                            this,
                            if (startNow) "Yoinking…" else "Added to the queue.",
                            Toast.LENGTH_SHORT,
                        ).show()
                        finish()
                    },
                )
            }
        }
    }

    private fun enqueue(url: String, mode: Mode, fmt: Fmt, quality: String, startNow: Boolean, title: String?) {
        Queue.add(
            YoinkJob(
                url = url,
                format = fmt,
                quality = Qualities.sanitize(fmt, quality),
                modeId = mode.id,
                title = title,
            )
        )
        RoutineEngine.fire(applicationContext, TriggerKind.LINK_SHARED)
        if (startNow && !mode.holdInQueue && !Queue.paused.value) {
            YoinkService.kick(applicationContext)
        }
    }

    /**
     * Direct Share puts one row per mode on the share sheet; whichever row was
     * tapped decides the mode, otherwise the active one wins.
     */
    private fun resolveMode(intent: Intent?): Mode {
        val fromExtra = intent?.getStringExtra(EXTRA_MODE_ID)
        val fromShortcut = intent?.getStringExtra(EXTRA_SHORTCUT_ID)?.removePrefix("mode:")
        return Automation.modeById(fromExtra ?: fromShortcut ?: Automation.activeModeId.value)
    }

    private fun incomingText(intent: Intent?): CharSequence? = when (intent?.action) {
        Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
        Intent.ACTION_PROCESS_TEXT -> intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)
        ACTION_FROM_CLIPBOARD -> clipboardText()
        else -> intent?.getStringExtra(Intent.EXTRA_TEXT) ?: intent?.dataString
    }

    /** Readable here because this activity is on screen and has focus; not from a service. */
    private fun clipboardText(): CharSequence? {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        val clip = clipboard.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        return runCatching { clip.getItemAt(0).coerceToText(this) }.getOrNull()
    }

    companion object {
        const val ACTION_FROM_CLIPBOARD = "com.pylo.yoinker.share.FROM_CLIPBOARD"
        const val EXTRA_MODE_ID = "modeId"

        /** Intent.EXTRA_SHORTCUT_ID, written out so it also reads on API 24-28. */
        private const val EXTRA_SHORTCUT_ID = "android.intent.extra.shortcut.ID"
    }
}

@Composable
private fun ShareSheet(
    url: String,
    mode: Mode,
    onDismiss: () -> Unit,
    onYoink: (Fmt, String, Boolean, String?) -> Unit,
) {
    val context = LocalContext.current
    var format by remember { mutableStateOf(mode.format) }
    var quality by remember { mutableStateOf(mode.quality) }
    var peek by remember { mutableStateOf<PeekState>(PeekState.Loading) }

    LaunchedEffect(url) {
        peek = PeekState.Loading
        val result = withContext(Dispatchers.IO) { YoinkEngine.peek(context.applicationContext, url) }
        peek = result.fold(
            onSuccess = { PeekState.Loaded(it) },
            onFailure = { PeekState.Failed(it.message ?: "Couldn't read that link.") },
        )
    }

    val title = (peek as? PeekState.Loaded)?.info?.title

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Surface(
            color = MaterialTheme.colorScheme.background,
            shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp),
            tonalElevation = 0.dp,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp, bottom = 18.dp)
                    .navigationBarsPadding(),
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 12.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF2C2418)),
                )

                Text("🪝  Yoink", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = "${mode.emoji} ${mode.name} mode",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YkInkFaint,
                )

                Spacer(Modifier.height(14.dp))
                LinkPreview(url = url, state = peek)

                SectionLabel("Format")
                FormatToggle(format = format) {
                    format = it
                    quality = Qualities.default(it)
                }

                SectionLabel("Quality")
                QualityPicker(format = format, quality = quality) { quality = it }

                Spacer(Modifier.height(18.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { onYoink(format, quality, false, title) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1f),
                    ) { Text("Queue it") }

                    Button(
                        onClick = { onYoink(format, quality, true, title) },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.weight(1.4f),
                    ) { Text("⬇  Yoink now") }
                }

                Text(
                    text = if (format.isAudio) "Saves to Music/Yoinker" else "Saves to Movies/Yoinker",
                    style = MaterialTheme.typography.bodyMedium,
                    color = YkInkFaint,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}
