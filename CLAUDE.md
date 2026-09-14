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

## The download engine

yt-dlp and ffmpeg run on the device via `youtubedl-android`. When changing format
selection in `YoinkEngine`, choose streams by **codec**, never by container: `ext=mp4`
says nothing about what's inside, and yt-dlp's default sorting prefers VP9 and AV1
over H.264 — which stock Android players won't draw from inside an MP4. `Mp4Probe`
guards this; keep its tests passing.
