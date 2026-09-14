package com.pylo.yoinker.automation

import com.pylo.yoinker.core.Fmt
import kotlinx.serialization.Serializable
import java.util.UUID

/**
 * A Mode is how Yoinker behaves right now: what it downloads, how good, over which
 * network, and how loudly. Switching mode is a single decision that changes every
 * yoink afterwards — including the ones that arrive through the share sheet.
 */
@Serializable
data class Mode(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val emoji: String = "🪝",
    val format: Fmt = Fmt.MP3,
    val quality: String = "192K",
    /** Hold downloads until the phone is on an unmetered connection. */
    val wifiOnly: Boolean = false,
    /** Shared links start downloading immediately instead of opening the sheet. */
    val instantShare: Boolean = false,
    /** Shared links land in the queue but don't start on their own. */
    val holdInQueue: Boolean = false,
    /** No sound for finished files. */
    val quiet: Boolean = false,
    /** Re-encode a download the phone couldn't otherwise play, instead of warning. */
    val forcePlayable: Boolean = true,
    val builtIn: Boolean = false,
) {
    val summary: String
        get() = buildList {
            add("${format.label} · ${com.pylo.yoinker.core.Qualities.labelOf(format, quality).substringBefore(" —")}")
            if (wifiOnly) add("Wi-Fi only")
            if (instantShare) add("instant")
            if (holdInQueue) add("hold in queue")
            if (quiet) add("quiet")
        }.joinToString(" · ")
}

enum class TriggerKind(val label: String) {
    LINK_SHARED("A link is shared to Yoinker"),
    DOWNLOAD_FINISHED("A download finishes"),
    DOWNLOAD_FAILED("A download fails"),
    CHARGER_CONNECTED("The charger is plugged in"),
    CHARGER_DISCONNECTED("The charger is unplugged"),
    UNMETERED_CONNECTED("Wi-Fi connects"),
    HEADPHONES_CONNECTED("Headphones are plugged in"),
    DND_ON("Do Not Disturb turns on"),
    DND_OFF("Do Not Disturb turns off"),
    TIME_OF_DAY("At a time of day"),
    BOOT_COMPLETED("The phone finishes starting up"),
    EXTERNAL("Another app asks for it");

    val needsTime: Boolean get() = this == TIME_OF_DAY
    val needsTag: Boolean get() = this == EXTERNAL
}

enum class ConditionKind(val label: String) {
    ON_WIFI("Only on Wi-Fi"),
    ON_MOBILE("Only on mobile data"),
    CHARGING("Only while charging"),
    NOT_CHARGING("Only on battery"),
    BATTERY_ABOVE("Only above a battery level"),
    DND_ON("Only while Do Not Disturb is on"),
    DND_OFF("Only while Do Not Disturb is off"),
    QUEUE_NOT_EMPTY("Only if something is queued"),
    MODE_IS("Only in a particular mode");

    val needsNumber: Boolean get() = this == BATTERY_ABOVE
    val needsMode: Boolean get() = this == MODE_IS
}

enum class ActionKind(val label: String) {
    SET_MODE("Switch mode"),
    YOINK_CLIPBOARD("Yoink the link in the clipboard"),
    START_QUEUE("Start the queue"),
    PAUSE_QUEUE("Pause the queue"),
    RESUME_QUEUE("Resume the queue"),
    CLEAR_FINISHED("Clear finished downloads"),
    UPDATE_ENGINE("Update the download engine"),
    NOTIFY("Show me a note");

    val needsMode: Boolean get() = this == SET_MODE
    val needsText: Boolean get() = this == NOTIFY
}

@Serializable
data class Trigger(
    val kind: TriggerKind = TriggerKind.LINK_SHARED,
    val hour: Int = 9,
    val minute: Int = 0,
    /** Calendar.DAY_OF_WEEK values; empty means every day. */
    val days: List<Int> = emptyList(),
    /** For EXTERNAL: the tag another app names when it fires this routine. */
    val tag: String = "",
) {
    val timeLabel: String get() = "%02d:%02d".format(hour, minute)
}

@Serializable
data class Condition(
    val kind: ConditionKind = ConditionKind.ON_WIFI,
    val number: Int = 50,
    val modeId: String = "",
)

@Serializable
data class Action(
    val kind: ActionKind = ActionKind.START_QUEUE,
    val modeId: String = "",
    val text: String = "",
)

/**
 * A Routine is "when this happens, and these things are true, do that" — the same
 * shape Modes and Routines uses, so the two read the same way side by side.
 */
@Serializable
data class Routine(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val enabled: Boolean = true,
    val trigger: Trigger = Trigger(),
    val conditions: List<Condition> = emptyList(),
    val actions: List<Action> = emptyList(),
    val builtIn: Boolean = false,
    val lastRunAt: Long = 0L,
)

object Defaults {

    val modes: List<Mode> = listOf(
        Mode(
            id = "standard", name = "Standard", emoji = "🪝",
            format = Fmt.MP3, quality = "192K", builtIn = true,
        ),
        Mode(
            id = "music", name = "Music", emoji = "🎵",
            format = Fmt.MP3, quality = "320K", wifiOnly = true, instantShare = true, builtIn = true,
        ),
        Mode(
            id = "video", name = "Video", emoji = "🎬",
            format = Fmt.MP4, quality = "1080", builtIn = true,
        ),
        Mode(
            id = "saver", name = "Data saver", emoji = "🪫",
            format = Fmt.MP4, quality = "480", wifiOnly = true, holdInQueue = true, builtIn = true,
        ),
        Mode(
            id = "night", name = "Night", emoji = "🌙",
            format = Fmt.MP3, quality = "192K", holdInQueue = true, quiet = true, builtIn = true,
        ),
    )

    /**
     * The routines Yoinker ships with. They lean on the state Samsung's Modes
     * already change — Do Not Disturb, Wi-Fi, the charger — so a Sleep or Driving
     * mode on the phone quietly moves Yoinker with it.
     */
    val routines: List<Routine> = listOf(
        Routine(
            id = "wifi-catchup", name = "Catch up on Wi-Fi", builtIn = true,
            trigger = Trigger(TriggerKind.UNMETERED_CONNECTED),
            conditions = listOf(Condition(ConditionKind.QUEUE_NOT_EMPTY)),
            actions = listOf(Action(ActionKind.RESUME_QUEUE), Action(ActionKind.START_QUEUE)),
        ),
        Routine(
            id = "charger-binge", name = "Download while charging", builtIn = true,
            trigger = Trigger(TriggerKind.CHARGER_CONNECTED),
            conditions = listOf(Condition(ConditionKind.QUEUE_NOT_EMPTY), Condition(ConditionKind.ON_WIFI)),
            actions = listOf(Action(ActionKind.RESUME_QUEUE), Action(ActionKind.START_QUEUE)),
        ),
        Routine(
            id = "dnd-night", name = "Go quiet with Do Not Disturb", builtIn = true,
            trigger = Trigger(TriggerKind.DND_ON),
            actions = listOf(Action(ActionKind.SET_MODE, modeId = "night"), Action(ActionKind.PAUSE_QUEUE)),
        ),
        Routine(
            id = "dnd-off", name = "Back to normal afterwards", builtIn = true,
            trigger = Trigger(TriggerKind.DND_OFF),
            actions = listOf(
                Action(ActionKind.SET_MODE, modeId = "standard"),
                Action(ActionKind.RESUME_QUEUE),
                Action(ActionKind.START_QUEUE),
            ),
        ),
        Routine(
            id = "headphones-music", name = "Headphones mean music", enabled = false, builtIn = true,
            trigger = Trigger(TriggerKind.HEADPHONES_CONNECTED),
            actions = listOf(Action(ActionKind.SET_MODE, modeId = "music")),
        ),
        Routine(
            id = "nightly-engine", name = "Keep the engine fresh", builtIn = true,
            trigger = Trigger(TriggerKind.TIME_OF_DAY, hour = 3, minute = 30),
            conditions = listOf(Condition(ConditionKind.ON_WIFI)),
            actions = listOf(Action(ActionKind.UPDATE_ENGINE)),
        ),
    )
}
