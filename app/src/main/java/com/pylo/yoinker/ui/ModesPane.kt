package com.pylo.yoinker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.pylo.yoinker.automation.Automation
import com.pylo.yoinker.automation.Hooks
import com.pylo.yoinker.automation.Mode
import com.pylo.yoinker.core.Qualities
import com.pylo.yoinker.ui.theme.Yk

@Composable
fun ModesPane() {
    val context = LocalContext.current
    val modes by Automation.modes.collectAsState()
    val activeId by Automation.activeModeId.collectAsState()
    var editing by remember { mutableStateOf<Mode?>(null) }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Text(
            text = "Modes",
            style = MaterialTheme.typography.displaySmall,
            color = Yk.Ink,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "How Yoinker behaves right now — format, quality, and whether a shared " +
                "link starts on its own. Routines switch between them for you.",
            style = MaterialTheme.typography.bodyMedium,
            color = Yk.InkFaint,
            modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
        )

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 14.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(modes, key = { it.id }) { mode ->
                ModeCard(
                    mode = mode,
                    active = mode.id == activeId,
                    onActivate = {
                        Automation.setActiveMode(mode.id)
                        Hooks.refreshShareTargets(context)
                    },
                    onEdit = { editing = mode },
                    onDuplicate = {
                        editing = mode.copy(
                            id = java.util.UUID.randomUUID().toString(),
                            name = "${mode.name} copy",
                            builtIn = false,
                        )
                    },
                    onDelete = { Automation.deleteMode(mode.id) },
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                GhostButton(
                    text = "+   New mode",
                    tint = Yk.GoldBright,
                    modifier = Modifier.fillMaxWidth(),
                ) { editing = Mode(name = "New mode") }
            }
        }
    }

    editing?.let { mode ->
        ModeEditor(
            mode = mode,
            onDismiss = { editing = null },
            onSave = {
                Automation.upsertMode(it)
                Hooks.refreshShareTargets(context)
                editing = null
            },
        )
    }
}

@Composable
private fun ModeCard(
    mode: Mode,
    active: Boolean,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    YkCard(
        brush = if (active) Yk.goldFaint else Yk.card,
        border = if (active) Yk.Gold else Yk.Line,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onActivate),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${mode.emoji}   ${mode.name}",
                style = MaterialTheme.typography.titleMedium,
                color = Yk.Ink,
            )
            Spacer(Modifier.weight(1f))
            if (active) Text("ACTIVE", style = MaterialTheme.typography.labelSmall, color = Yk.GoldBright)
        }
        Spacer(Modifier.height(4.dp))
        Text(mode.summary, style = MaterialTheme.typography.bodyMedium, color = Yk.InkFaint)

        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (mode.builtIn) {
                TextAction("Duplicate", tint = Yk.InkFaint, onClick = onDuplicate)
            } else {
                TextAction("Edit", onClick = onEdit)
                TextAction("Delete", tint = Yk.InkFaint, onClick = onDelete)
            }
        }
    }
}

@Composable
private fun ModeEditor(mode: Mode, onDismiss: () -> Unit, onSave: (Mode) -> Unit) {
    var draft by remember(mode.id) { mutableStateOf(mode) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(draft.copy(name = draft.name.ifBlank { "Untitled mode" })) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text(if (mode.builtIn) "Duplicate mode" else "Mode") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = draft.emoji,
                        onValueChange = { draft = draft.copy(emoji = it.take(2)) },
                        label = { Text("Icon") },
                        singleLine = true,
                        modifier = Modifier.weight(0.8f),
                    )
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = { draft = draft.copy(name = it) },
                        label = { Text("Name") },
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                    )
                }

                SectionLabel("Format")
                FormatToggle(format = draft.format) {
                    draft = draft.copy(format = it, quality = Qualities.default(it))
                }

                SectionLabel("Quality")
                QualityPicker(format = draft.format, quality = draft.quality) {
                    draft = draft.copy(quality = it)
                }

                Spacer(Modifier.height(8.dp))
                ToggleRow("Only download on Wi-Fi", draft.wifiOnly) { draft = draft.copy(wifiOnly = it) }
                ToggleRow("Shared links start immediately", draft.instantShare) {
                    draft = draft.copy(instantShare = it, holdInQueue = if (it) false else draft.holdInQueue)
                }
                ToggleRow("Hold shared links in the queue", draft.holdInQueue) {
                    draft = draft.copy(holdInQueue = it, instantShare = if (it) false else draft.instantShare)
                }
                ToggleRow("Silent notifications", draft.quiet) { draft = draft.copy(quiet = it) }
                ToggleRow("Convert videos the phone can't play", draft.forcePlayable) {
                    draft = draft.copy(forcePlayable = it)
                }
            }
        },
    )
}

@Composable
fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
