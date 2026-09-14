# Working on Yoinker for Android

## Commits

Never put a Claude session link — `https://claude.ai/code/session_…` — in a commit
message, a pull request, a code comment, a doc, or anything else that lands in this
repository or gets sent anywhere. Not in any form. The `Co-Authored-By:` trailer is
fine; the session link is not.

## Building

- Needs the Android SDK with **API 36** and JDK 17. Put `sdk.dir=…` in
  `local.properties`.
- `./gradlew assembleDebug` builds one APK per ABI plus a universal one. The
  universal build is ~240 MB because each architecture carries its own python and
  ffmpeg — pass `-Pyoinker.universalApk=false` to skip it, as CI does.
- `./gradlew testDebugUnitTest lintDebug` before pushing. Lint is expected to pass
  with zero errors.

## Design

The look is charred wood and gold, inherited from the desktop build — every neutral
has red in it, so nothing drifts blue-grey. Tokens live in `ui/theme/Theme.kt` (the
`Yk` object) and the component vocabulary in `ui/Common.kt`: `YkCard`, `GoldButton`,
`GhostButton`, `Pill`, `YkProgress`, `EmptyState`, `Thumb`, `TextAction`. Build
screens out of those rather than raw Material components, or the app stops looking
like one thing.

One gold gradient button per screen — it's the only piece of polished brass, and a
second one makes neither of them the answer. Emoji render in their own colours and
will fight the palette; the app's own vectors, tinted, stay in key.

`./gradlew recordPaparazziDebug` renders the design to PNGs under
`app/src/test/snapshots/` so it can be looked at without a device. They're a design
tool, not a gate — a plain `test` run renders them without comparing, which still
catches a screen that throws at composition time.

## The download engine

yt-dlp and ffmpeg run on the device via `youtubedl-android`. When changing format
selection in `YoinkEngine`, choose streams by **codec**, never by container: `ext=mp4`
says nothing about what's inside, and yt-dlp's default sorting prefers VP9 and AV1
over H.264 — which stock Android players won't draw from inside an MP4. `Mp4Probe`
guards this; keep its tests passing.
