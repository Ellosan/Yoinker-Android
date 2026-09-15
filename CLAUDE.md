# Working on Yoinker for Android

## Commits

Never put a Claude session link — `https://claude.ai/code/session_…` — in a commit
message, a pull request, a code comment, a doc, or anything else that lands in this
repository or gets sent anywhere. Not in any form. The `Co-Authored-By:` trailer is
fine; the session link is not.

## Building

- Needs the Android SDK with **API 36** and **JDK 21** (Paparazzi requires it; the
  app still targets JVM 17 bytecode). Put `sdk.dir=…` in `local.properties`.
- `./gradlew assembleDebug` builds one APK per ABI plus a universal one. The
  universal build is ~240 MB because each architecture carries its own python and
  ffmpeg — pass `-Pyoinker.universalApk=false` to skip it, as CI does.
- `./gradlew testDebugUnitTest lintDebug` before pushing. Lint is expected to pass
  with zero errors, and the tests are expected to be green — they are the only
  check that runs, since nothing here has a device.
- Release builds are signed only if a key is present: put `storeFile`,
  `storePassword`, `keyAlias` and `keyPassword` in `keystore.properties` beside
  the project, or pass `YOINKER_KEYSTORE`, `YOINKER_KEYSTORE_PASSWORD`,
  `YOINKER_KEY_ALIAS`, `YOINKER_KEY_PASSWORD` in the environment. Without one the
  release build still runs and comes out unsigned. Never commit either.

## Design

The look is charred wood and gold, inherited from the desktop build — every neutral
has red in it, so nothing drifts blue-grey. Tokens live in `ui/theme/Theme.kt` — the `Yk`
object for colour and gradients, `Space` for the one spacing scale, `YkType` for the
three font families, `Motion` for the spring specs. The component vocabulary is in
`ui/Common.kt`: `ScreenTitle`, `SectionLabel`, `YkCard`, `IconTile`, `PrimaryButton`,
`SecondaryButton`, `TextAction`, `Pill`, `FormatToggle`, `QualityPicker`, `YkProgress`,
`StatusChip`, `EmptyState`, `Thumb`, `LinkPreview`. Build screens out of those rather
than raw Material components, or the app stops looking like one thing.

Rules worth not relearning:

- **One gold button per screen.** It's the only piece of polished brass; a second
  one means neither is the answer.
- **No emoji in the chrome.** They render in their own colours and will fight the
  palette — a stock link emoji turned out to be the only cold colour in the app.
  Use a drawn icon, tinted.
- **Text from the internet keeps the system font.** `MaterialTheme.typography.x
  .asContent()` does it. Video titles and channel names arrive in every script
  there is, and the bundled Latin faces would render them as empty boxes.
- **Spacing comes from `Space`**, not from whatever number looked right.

`./gradlew recordPaparazziDebug` renders the design to PNGs under
`app/src/test/snapshots/` so it can be looked at without a device. They're a design
tool, not a gate — a plain `test` run renders them without comparing, which still
catches a screen that throws at composition time.

## Tests

`app/src/test/` is JVM-only — there is no instrumentation suite and no device in
CI, so anything that can be pulled into a plain unit test should be. `UrlsTest`
guards the link parsing every share goes through, `ConverterTest` guards the
encode-versus-copy decisions, `Mp4ProbeTest` the codec check, `QueueTest` the
state machine.

`Prefs` returns defaults and drops writes until `init` has run, rather than
throwing. Keep it that way: receivers, tiles and activities are all entry points
into this process, and it is also what makes the queue testable at all.

## The download engine

yt-dlp and ffmpeg run on the device via `youtubedl-android`. When changing format
selection in `YoinkEngine`, choose streams by **codec**, never by container: `ext=mp4`
says nothing about what's inside, and yt-dlp's default sorting prefers VP9 and AV1
over H.264 — which stock Android players won't draw from inside an MP4. `Mp4Probe`
guards this; keep its tests passing.
