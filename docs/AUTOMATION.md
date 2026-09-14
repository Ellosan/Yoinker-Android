# Automating Yoinker

Yoinker can be driven from outside: by Samsung's Modes and Routines, by Tasker,
MacroDroid or Automate, by a launcher shortcut, or by `adb` while you're testing.

There are three ways in, in order of how little setup they need.

---

## 1. Follow the phone's Modes — nothing to set up

Device Modes (Sleep, Driving, Work, Focus…) all switch **Do Not Disturb** on and off,
and Yoinker's built-in routines listen for exactly that:

- *Go quiet with Do Not Disturb* — switch to Night mode, pause the queue.
- *Back to normal afterwards* — switch back to Standard and start the queue again.

Open **Routines** in Yoinker to change what they do, or switch them off.

One caveat worth knowing: Android only delivers the Do Not Disturb and headphone
events to an app that is already running. If you want those routines to fire while
Yoinker is closed, turn on **Routines → Automation hooks → Watch for triggers in the
background**. It costs a permanent low-priority notification, which is why it's off
until you ask.

The charger, Wi-Fi, time-of-day and boot triggers need none of that — the system
wakes Yoinker for those on its own.

---

## 2. Modes and Routines: "open an app" actions

Samsung's Modes and Routines has no public plugin API. Its **Then** step can open an
app, but it cannot pass one any instructions — which is why apps rarely show up there
with real actions.

Yoinker works around it by publishing each action as its own openable entry. In
Yoinker, go to **Routines → Automation hooks → Publish Yoinker actions**. Six entries
appear on the system:

| Entry | What happens when it's opened |
| --- | --- |
| Yoinker: yoink clipboard | Yoinks the link on the clipboard, in the current mode |
| Yoinker: start queue | Un-pauses and works through the queue |
| Yoinker: pause queue | Stops the current download and pauses |
| Yoinker: mode 1 / 2 / 3 | Switches to whichever mode you assigned to that slot |

Assign the three mode slots in the same dialog.

Then, in **Modes and Routines**:

1. Create a routine, pick your **If** condition as usual.
2. Under **Then**, choose **Apps** → **Open an app**.
3. Pick the Yoinker entry you want. It runs the action and closes immediately —
   nothing lands on screen.

These entries also show up in your launcher and in Bixby, so the same trick works for
a home-screen icon or a spoken command. Turning the switch back off removes them all.

---

## 3. Broadcast intents — Tasker, MacroDroid, Automate

Yoinker's automation API is a plain broadcast receiver. Broadcasts must name the
component explicitly (Android stopped delivering implicit ones to sleeping apps):

```
Package:  com.pylo.yoinker
Class:    com.pylo.yoinker.automation.AutomationReceiver
```

| Action | Extras | What it does |
| --- | --- | --- |
| `com.pylo.yoinker.action.YOINK` | `url` (string), `mode` (optional) | Queues a link and starts it |
| `com.pylo.yoinker.action.YOINK_CLIPBOARD` | — | Yoinks the link on the clipboard |
| `com.pylo.yoinker.action.SET_MODE` | `mode` | Switches mode |
| `com.pylo.yoinker.action.START_QUEUE` | — | Un-pauses and works the queue |
| `com.pylo.yoinker.action.PAUSE_QUEUE` | — | Stops and pauses |
| `com.pylo.yoinker.action.RESUME_QUEUE` | — | Un-pauses without starting |
| `com.pylo.yoinker.action.CLEAR_FINISHED` | — | Clears finished downloads |
| `com.pylo.yoinker.action.UPDATE_ENGINE` | — | Updates yt-dlp |
| `com.pylo.yoinker.action.TRIGGER` | `tag` | Fires your own routines (see below) |

`mode` takes either a mode's name as shown in the app ("Music") or its id
("music") — whichever is easier to type where you are.

### Trying it from a terminal

```bash
adb shell am broadcast \
  -n com.pylo.yoinker/.automation.AutomationReceiver \
  -a com.pylo.yoinker.action.YOINK \
  --es url "https://example.com/watch?v=xyz" \
  --es mode music

adb shell am broadcast \
  -n com.pylo.yoinker/.automation.AutomationReceiver \
  -a com.pylo.yoinker.action.SET_MODE --es mode "Data saver"
```

### In Tasker

**Action → System → Send Intent**

- Action: `com.pylo.yoinker.action.YOINK`
- Extra: `url:%par1` (or whatever variable holds the link)
- Package: `com.pylo.yoinker`
- Class: `com.pylo.yoinker.automation.AutomationReceiver`
- Target: **Broadcast Receiver**

MacroDroid (*Send Intent*, target Broadcast) and Automate (*Send broadcast* block)
take the same four fields.

### Your own triggers

`com.pylo.yoinker.action.TRIGGER` with a `tag` fires any Yoinker routine whose
trigger is **"Another app asks for it"** with a matching tag. So a routine built in
Tasker can decide *when*, and Yoinker's routine decides *what* — conditions and all.

---

## 4. Activities and shortcuts

For anything that launches activities rather than sending broadcasts:

```
Action:  com.pylo.yoinker.action.RUN
Class:   com.pylo.yoinker.automation.AutomationActivity
Extras:  action = YOINK_CLIPBOARD | START_QUEUE | PAUSE_QUEUE | RESUME_QUEUE
                | CLEAR_FINISHED | UPDATE_ENGINE | SET_MODE | NOTIFY
         mode   = a mode id, when action is SET_MODE
         text   = the message, when action is NOTIFY
```

It shows no window: it runs the action, says so in a toast, and finishes.

Sharing a link to Yoinker also works from anything that can send `ACTION_SEND` with
`text/plain` — that's the same path the share sheet uses, and it honours the current
mode, including instant-start.

---

## A note on what's exported

The broadcast receiver is open to any app on the phone, because an automation API
that required a permission no automation app can hold wouldn't be an automation API.
The worst another app can do through it is queue a download or change your mode. It
can't read anything back, and it can't reach your files.
