package com.pylo.yoinker.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Extension
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pylo.yoinker.automation.Action
import com.pylo.yoinker.automation.ActionKind
import com.pylo.yoinker.automation.Automation
import com.pylo.yoinker.automation.AutomationService
import com.pylo.yoinker.automation.Condition
import com.pylo.yoinker.automation.ConditionKind
import com.pylo.yoinker.automation.Hooks
import com.pylo.yoinker.automation.Routine
import com.pylo.yoinker.automation.Trigger
import com.pylo.yoinker.automation.TriggerKind
import com.pylo.yoinker.core.Prefs
import com.pylo.yoinker.ui.theme.Space
import com.pylo.yoinker.ui.theme.Yk

@Composable
fun RoutinesPane() {
    val context = LocalContext.current
    val routines by Automation.routines.collectAsState()
    var editing by remember { mutableStateOf<Routine?>(null) }
    var showHooks by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().padding(horizontal = Space.gutter)) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(Space.md),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
            modifier = Modifier.weight(1f),
        ) {
            item {
                ScreenTitle(
                    title = "Routines",
                    subtitle = "When something happens on this phone, Yoinker does something " +
                        "about it. The built-in ones follow what your device Modes already " +
                        "change — Do Not Disturb, Wi-Fi, the charger.",
                )
            }

            items(routines, key = { it.id }) { routine ->
                RoutineCard(
                    routine = routine,
                    onToggle = { enabled ->
                        Automation.setRoutineEnabled(routine.id, enabled)
                        Automation.applySchedules(context.applicationContext)
                    },
                    onEdit = { editing = routine },
                    onDelete = {
                        Automation.deleteRoutine(routine.id)
                        Automation.applySchedules(context.applicationContext)
                    },
                )
            }

            item {
                Spacer(Modifier.height(4.dp))
                SecondaryButton(
                    text = "New routine",
                    icon = Icons.Rounded.Add,
                    tint = Yk.GoldBright,
                    modifier = Modifier.fillMaxWidth(),
                ) { editing = Routine(name = "New routine") }
                Spacer(Modifier.height(10.dp))
                SecondaryButton(
                    text = "Automation hooks",
                    icon = Icons.Rounded.Extension,
                    modifier = Modifier.fillMaxWidth(),
                ) { showHooks = true }
            }
        }
    }

    editing?.let { routine ->
        RoutineEditor(
            routine = routine,
            onDismiss = { editing = null },
            onSave = {
                Automation.upsertRoutine(it)
                Automation.applySchedules(context.applicationContext)
                editing = null
            },
        )
    }

    if (showHooks) {
        HooksDialog(onDismiss = { showHooks = false })
    }
}

@Composable
private fun RoutineCard(
    routine: Routine,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    YkCard(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(routine.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Switch(checked = routine.enabled, onCheckedChange = onToggle)
        }

        val when_ = buildString {
            append("When ")
            append(routine.trigger.kind.label.replaceFirstChar { it.lowercase() })
            if (routine.trigger.kind.needsTime) append(" (${routine.trigger.timeLabel})")
            if (routine.trigger.kind.needsTag && routine.trigger.tag.isNotBlank()) {
                append(" · tag \"${routine.trigger.tag}\"")
            }
        }
        Text(when_, style = MaterialTheme.typography.bodyMedium, color = Yk.InkFaint)

        val then = routine.actions.joinToString(" · ") { action ->
            when (action.kind) {
                ActionKind.SET_MODE -> "switch to ${Automation.modeById(action.modeId).name}"
                else -> action.kind.label.replaceFirstChar { it.lowercase() }
            }
        }
        if (then.isNotEmpty()) {
            Text("Then $then", style = MaterialTheme.typography.bodyMedium, color = Yk.InkFaint)
        }

        Spacer(Modifier.height(4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            TextAction("Edit", onClick = onEdit)
            if (!routine.builtIn) {
                TextAction("Delete", tint = Yk.InkFaint, onClick = onDelete)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RoutineEditor(routine: Routine, onDismiss: () -> Unit, onSave: (Routine) -> Unit) {
    var draft by remember(routine.id) { mutableStateOf(routine) }
    val modes by Automation.modes.collectAsState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onSave(draft.copy(name = draft.name.ifBlank { "Untitled routine" })) }) {
                Text("Save")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        title = { Text("Routine") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = { draft = draft.copy(name = it) },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                SectionLabel("When")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    TriggerKind.entries.forEach { kind ->
                        Pill(
                            label = kind.label,
                            selected = draft.trigger.kind == kind,
                        ) { draft = draft.copy(trigger = draft.trigger.copy(kind = kind)) }
                    }
                }

                if (draft.trigger.kind.needsTime) {
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        NumberField("Hour", draft.trigger.hour, 0..23) {
                            draft = draft.copy(trigger = draft.trigger.copy(hour = it))
                        }
                        NumberField("Minute", draft.trigger.minute, 0..59) {
                            draft = draft.copy(trigger = draft.trigger.copy(minute = it))
                        }
                    }
                }

                if (draft.trigger.kind.needsTag) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = draft.trigger.tag,
                        onValueChange = { draft = draft.copy(trigger = draft.trigger.copy(tag = it)) },
                        label = { Text("Tag another app sends") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                SectionLabel("Only if")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    ConditionKind.entries.forEach { kind ->
                        val on = draft.conditions.any { it.kind == kind }
                        Pill(
                            selected = on,
                            onClick = {
                                draft = draft.copy(
                                    conditions = if (on) {
                                        draft.conditions.filterNot { it.kind == kind }
                                    } else {
                                        draft.conditions + Condition(kind)
                                    },
                                )
                            },
                            label = kind.label,
                        )
                    }
                }

                draft.conditions.firstOrNull { it.kind.needsNumber }?.let { condition ->
                    Spacer(Modifier.height(8.dp))
                    NumberField("Battery at least (%)", condition.number, 1..100) { value ->
                        draft = draft.copy(
                            conditions = draft.conditions.map {
                                if (it.kind == condition.kind) it.copy(number = value) else it
                            },
                        )
                    }
                }

                draft.conditions.firstOrNull { it.kind.needsMode }?.let { condition ->
                    SectionLabel("In mode")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        modes.forEach { mode ->
                            Pill(
                                selected = condition.modeId == mode.id,
                                onClick = {
                                    draft = draft.copy(
                                        conditions = draft.conditions.map {
                                            if (it.kind == condition.kind) it.copy(modeId = mode.id) else it
                                        },
                                    )
                                },
                                label = "${mode.emoji} ${mode.name}",
                            )
                        }
                    }
                }

                SectionLabel("Then")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    ActionKind.entries.forEach { kind ->
                        val on = draft.actions.any { it.kind == kind }
                        Pill(
                            selected = on,
                            onClick = {
                                draft = draft.copy(
                                    actions = if (on) {
                                        draft.actions.filterNot { it.kind == kind }
                                    } else {
                                        draft.actions + Action(kind)
                                    },
                                )
                            },
                            label = kind.label,
                        )
                    }
                }

                draft.actions.firstOrNull { it.kind.needsMode }?.let { action ->
                    SectionLabel("Switch to")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        modes.forEach { mode ->
                            Pill(
                                selected = action.modeId == mode.id,
                                onClick = {
                                    draft = draft.copy(
                                        actions = draft.actions.map {
                                            if (it.kind == action.kind) it.copy(modeId = mode.id) else it
                                        },
                                    )
                                },
                                label = "${mode.emoji} ${mode.name}",
                            )
                        }
                    }
                }

                draft.actions.firstOrNull { it.kind.needsText }?.let { action ->
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = action.text,
                        onValueChange = { value ->
                            draft = draft.copy(
                                actions = draft.actions.map {
                                    if (it.kind == action.kind) it.copy(text = value) else it
                                },
                            )
                        },
                        label = { Text("What should it say?") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
    )
}

@Composable
private fun NumberField(label: String, value: Int, range: IntRange, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = { raw ->
            text = raw.filter { it.isDigit() }.take(3)
            text.toIntOrNull()?.coerceIn(range)?.let(onChange)
        },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.width(150.dp),
    )
}

@Composable
private fun HooksDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val modes by Automation.modes.collectAsState()
    var hooksOn by remember { mutableStateOf(Prefs.hooksEnabled) }
    var watching by remember { mutableStateOf(Prefs.watchInBackground) }
    var slots by remember { mutableStateOf((1..3).map { Prefs.aliasMode(it) }) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Automation hooks") },
        text = {
            Column(Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState())) {
                Text(
                    text = "Modes & Routines can open an app as an action, but it can't hand one " +
                        "instructions. So Yoinker publishes each action as its own openable entry: " +
                        "turn this on and \"Yoinker: start queue\" shows up in the Modes & Routines " +
                        "app list, in Bixby, and in your launcher.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Yk.InkFaint,
                )

                Spacer(Modifier.height(10.dp))
                ToggleRow("Publish Yoinker actions", hooksOn) {
                    hooksOn = it
                    Hooks.setEnabled(context, it)
                }

                if (hooksOn) {
                    SectionLabel("Mode slots")
                    Text(
                        text = "The three \"switch mode\" entries point at these modes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Yk.InkFaint,
                    )
                    slots.forEachIndexed { index, assigned ->
                        val slot = index + 1
                        SectionLabel("Mode $slot")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(Space.sm),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                        ) {
                            modes.forEach { mode ->
                                Pill(
                                    selected = assigned == mode.id,
                                    onClick = {
                                        Hooks.assignSlot(context, slot, mode.id)
                                        slots = (1..3).map { Prefs.aliasMode(it) }
                                    },
                                    label = "${mode.emoji} ${mode.name}",
                                )
                            }
                        }
                    }
                }

                SectionLabel("Background watching")
                Text(
                    text = "Do Not Disturb and headphone triggers are only delivered to a running " +
                        "app. Turn this on and Yoinker keeps a quiet, permanent notification so those " +
                        "routines still fire when it's closed.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Yk.InkFaint,
                )
                Spacer(Modifier.height(6.dp))
                ToggleRow("Watch for triggers in the background", watching) {
                    watching = it
                    Prefs.watchInBackground = it
                    AutomationService.syncWithPreference(context)
                }

                SectionLabel("Other apps")
                Text(
                    text = "Tasker, MacroDroid and Automate can drive Yoinker directly with broadcast " +
                        "intents — see docs/AUTOMATION.md in the project for the full list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Yk.InkFaint,
                )
            }
        },
    )
}
