# 🪝 Yoinker for Android

Share a link, get a clean **MP3** or **MP4** — saved straight to your phone's music
library or gallery. A safe, local, ad-free stand-in for those sketchy online
"converter" sites with the pop-up ads and mystery downloads.

Everything runs on your phone. The only thing that leaves is yt-dlp's request to the
link you shared — no third-party site sees your clicks, and nothing gets installed
behind your back.

This is the Android port of [Yoinker](https://github.com/pylosreliquaries/Yoinker),
the desktop app. Same engine, same arguments, same files out the other end.

Part of [Pylo's Reliquaries](https://pylosreliquaries.github.io).

## What it does

- **Yoink from the share sheet.** Share a link from a browser, a chat, a video app —
  "Yoink" is right there. So is one row per mode, so "Yoink · Music" is a single tap.
- **MP3** (128 / 192 / 320 kbps) or **MP4** (Best / 1080p / 720p / 480p).
- Previews the title, channel and thumbnail before you commit.
- Downloads in the background with live progress, and saves into `Music/Yoinker`
  or `Movies/Yoinker` so files show up in your normal apps.
- A **queue**, so a handful of links can be lined up and left alone.
- **Modes** and **Routines** — see below.
- Select a link in any app and tap **Yoink** in the text menu.
- A Quick Settings tile that yoinks whatever link is on the clipboard.

Powered by **[yt-dlp](https://github.com/yt-dlp/yt-dlp)** (the download engine,
supports hundreds of sites) and **ffmpeg** (does the MP3 extraction and MP4
packaging), both running on the phone via
[youtubedl-android](https://github.com/JunkFood02/youtubedl-android).

## Modes

A mode is how Yoinker behaves right now — the format, the quality, whether a shared
link starts downloading on its own, and how loud it is about finishing. Five come
built in, and you can add your own:

| Mode | What it does |
| --- | --- |
| 🪝 Standard | MP3 192 kbps, asks before downloading |
| 🎵 Music | MP3 320 kbps, Wi-Fi only, starts the moment you share |
| 🎬 Video | MP4 1080p |
| 🪫 Data saver | MP4 480p, Wi-Fi only, holds links in the queue |
| 🌙 Night | Holds links in the queue, silent notifications |

## Routines

"When this happens, and these things are true, do that." The ones that ship:

- **Catch up on Wi-Fi** — anything queued starts when an unmetered connection appears.
- **Download while charging** — plug in on Wi-Fi and the queue runs.
- **Go quiet with Do Not Disturb** — switches to Night mode and pauses the queue.
- **Back to normal afterwards** — and back again when Do Not Disturb ends.
- **Headphones mean music** *(off by default)* — plug in, switch to Music mode.
- **Keep the engine fresh** — updates yt-dlp at 03:30 on Wi-Fi.

Triggers cover shared links, finished and failed downloads, the charger, Wi-Fi,
headphones, Do Not Disturb, a time of day, boot, and a call from another app.
Conditions cover network, charging, battery level, Do Not Disturb, the queue and the
current mode. Actions switch mode, run or pause the queue, yoink the clipboard,
update the engine, or just tell you something.

## Modes & Routines, Tasker, and friends

Samsung's **Modes and Routines** has no public plugin API — a routine can *open* an
app, but it can't hand one instructions. So Yoinker publishes each of its actions as
its own openable entry: turn on **Routines → Automation hooks → Publish Yoinker
actions**, and entries like "Yoinker: start queue" appear in the Modes and Routines
app list (and in Bixby, and in your launcher). Opening one runs that action and
closes again.

The other half is Yoinker following your device Modes on its own: Sleep, Driving and
the rest all switch Do Not Disturb on, and the built-in routines above react to that
— no setup needed.

Tasker, MacroDroid and Automate can drive Yoinker directly with broadcast intents.
**[docs/AUTOMATION.md](docs/AUTOMATION.md)** has the full list.

## How it looks

Charred wood and gold, carried over from the desktop build. Screen titles are set in
Space Grotesk and the interface in Inter, both bundled under the SIL Open Font
Licence — see [licenses/](licenses/). Text that comes from a website keeps the
system font on purpose: neither family covers every script, and a Japanese title set
in a Latin-only face is a row of empty boxes.

`./gradlew recordPaparazziDebug` renders the screens to PNGs without a device, which
is how the design gets checked rather than assumed.

## Build it

You need the Android SDK (API 36) and JDK 17+.

```bash
git clone https://github.com/Ellosan/Yoinker-Android
cd Yoinker-Android
echo "sdk.dir=/path/to/Android/sdk" > local.properties
./gradlew assembleDebug
```

APKs land in `app/build/outputs/apk/debug/`. There's one per architecture plus a
universal build — take `arm64-v8a` unless you know your phone is something else.
They're large (~80 MB) because a whole python runtime and ffmpeg ride along inside.

## Keeping the engine fresh

Sites change; yt-dlp updates often. Yoinker freshens itself in the background at most
once a day, and **⟳ Update engine** on the Yoink tab does it on demand — the one-click
fix for "it suddenly stopped working".

## How it fits together

```
app/src/main/java/com/pylo/yoinker/
  engine/      yt-dlp + ffmpeg on the phone: peek, download, self-update
  download/    the queue, the foreground service that works it, saving into the media store
  share/       the "Yoink" share-sheet entry and its sheet
  automation/  modes, routines, the rule engine, and the hooks other apps reach in through
  ui/          the app itself (Compose)
  core/        formats, settings, notifications, device state
```

## Fair use

Yoinker is a personal-use tool. Only download things you have the right to — respect
each site's terms of service and copyright.
