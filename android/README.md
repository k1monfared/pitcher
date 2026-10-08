# Android app

Native Kotlin + Jetpack Compose app for pitcher. Fully offline: no network
permission, no bundled downloader. Import audio or video (audio only is kept),
or share a file into pitcher from another app.

The touch-first "modern" interface is the default. The earlier multi-screen
interface is kept as a fallback and switchable in Settings; both share the same
view model and engine. A short guided tour runs on first launch and can be
replayed from Settings.

See [../docs/ANDROID_PLAN.md](../docs/ANDROID_PLAN.md) for the original app
design and [../docs/ANDROID_V2_PLAN.md](../docs/ANDROID_V2_PLAN.md) for the
touch-first redesign.

## Build

```
cd android
./gradlew :core:test          # note math, tuner, waveform, fader, shifter, MP3
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug  # debug APK
./gradlew :app:assembleRelease
```

Requires Java 17+ and the Android SDK (platform 36, build-tools). Create
`android/local.properties` with `sdk.dir=/path/to/Android/Sdk` (gitignored).

## Signing and releases

Release builds read these environment variables; without them the debug key is
used so local release builds still install:

```
PITCHER_KEYSTORE
PITCHER_KEYSTORE_PASSWORD
PITCHER_KEY_ALIAS
PITCHER_KEY_PASSWORD
```

`.github/workflows/android-release.yml` builds the release APK on `v*` tags and
attaches it to a GitHub Release. Set the matching repository secrets
(`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`) to sign.

## Screenshots

Screenshots are rendered from the real Compose UI headlessly with Roborazzi, no
device needed:

```
./gradlew :app:testDebugUnitTest -Proborazzi.test.record=true --tests "*ScreenshotTest"
```

They land in `app/build/screenshots/`. The published copies live in
`fastlane/metadata/android/en-US/images/phoneScreenshots/`; re-copy after
regenerating.

## Store metadata

F-Droid / store listing text lives in `fastlane/metadata/android/en-US/`
(title, short and full description, changelogs).

## Export formats

WAV, MP3, M4A (AAC), and Opus. WAV is lossless. FLAC is not offered on Android
because there is no usable pure-JVM encoder and MediaMuxer cannot container raw
FLAC. Exports are stereo, falling back to mono if memory is tight.
