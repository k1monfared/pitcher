# pitcher for Android

Change the key of any song while keeping the tempo, find the key with a tuner,
practice with loops and slower speeds, and save or share the result. Native
Kotlin and Jetpack Compose, fully offline: no network permission and no bundled
downloader.

Install the APK from the
[latest release](https://github.com/k1monfared/pitcher/releases/latest). Android
10 or newer. Release notes are in [CHANGELOG.md](CHANGELOG.md).

## Using the app

**Open a song.** Import an audio or video file from Library, or share one into
pitcher from another app. Only the audio is kept.

**Change the pitch.** Slide up or down anywhere on the big number. Slow movements
are fine and fast ones are coarse, and a flick keeps going until you touch the
screen again. The buttons above and below step by 1, 10, or 100 cents. Tap the
number to type an exact value, double-tap it to go back to the original. Turn on
snap to land on 20-cent steps with a strong pull to semitones. The interval is
spelled out above the number.

**Find the key.** Tap tuner. Fill a source and a target note by detecting the
note at the playhead, picking a bookmark, or typing a note such as `c#4` or
`a4+37`. The interval between them is shown, and Apply to slider sets the pitch.

**Move around the song.**

- Tap the waveform to jump, pinch to zoom, and drag to scrub (zoomed out) or pan
  (zoomed in).
- Long-press and drag for a precise scrub. Playback moves when you lift your finger.
- With follow on, a zoomed view scrolls with the playhead.
- The time of the playhead is shown beside it.

**Loops and bookmarks.**

- Drag across the lane above the waveform to make a loop.
- Drag a loop's edge to adjust it. Tap a loop to select it, which outlines it.
- Hold a loop to rename or delete it.
- The repeat button cycles through off, all loops, and the selected loop.
- Bookmark adds a pin at the playhead. Tap a pin to jump to it, hold and drag it to
  move it, and hold it to rename it. Named bookmarks show on the waveform.
- Loops and bookmarks lets you turn loops on and off and manage everything in one
  list.

**Speed.** Hold speed and slide to choose from 0.5x to 2x. Tap it to return to 1x.
The pitch does not change.

**Keep pitches.**

- The shelf at the bottom holds the original and every pitch you keep.
- When you are on a pitch you have not kept, a dashed pill shows it. Tap it to keep
  that pitch.
- A kept pitch is rendered in the background, so saving and sharing it later is
  quick.
- Tap a kept pitch to play it. Tap the one already playing to save it as a file.
- Hold a pitch to rename, share, or remove it.

**Save or share a file.**

- Choose the folder, file name, and format (WAV, MP3, M4A, or Opus), and whether to
  include only the enabled loops or to use the current play speed.
- Save file becomes Saved once that exact file exists, and Share reuses it.
- Each pitch saves on its own, so one long save never blocks another.
- Saved files use the same Sonic engine as playback, so they sound like the preview.

A guided tour runs the first time you open a song and can be replayed from
Settings.

## Build

```
cd android
./gradlew :core:test                 # note math, tuner, timeline, shelf, files
./gradlew :app:testFdroidDebugUnitTest     # storage, rendering, screenshots
./gradlew :app:assembleFdroidDebug   # debug APK
./gradlew :app:assembleFdroidRelease # minified release APK (GitHub and F-Droid)
```

Requires Java 17 or newer and the Android SDK (platform 36 and build-tools).
Create `android/local.properties` with `sdk.dir=/path/to/Android/Sdk`
(gitignored).

## How it works

- Playback is Media3 ExoPlayer in a media session service, with pitch and speed
  applied live by Sonic.
- Saving a file uses Media3's `SonicAudioProcessor`, the same engine, so the file
  matches the preview.
- A kept pitch is rendered once to a WAV in the app cache, streaming from the
  decoder through Sonic to the file, so memory stays flat for any song length.
- Saving cuts the loops and applies the speed from that render, then encodes. MP3
  uses a pure Java LAME port. M4A and Opus use the platform encoders.
- Decoding, playback, and M4A and Opus encoding use the phone's codecs, always
  preferring Android's own open-source codecs over a phone maker's closed-source
  ones (`media/Codecs.kt`). See [../docs/LEGALITY.md](../docs/LEGALITY.md).
- Waveforms are computed once per song and kept with the app's files.
- The library, pitches, saved files, loops, and bookmarks live in a small SQLite
  database (schema version 4).

## Flavours

- `fdroid` is the GitHub release and the F-Droid build. It shows the support links
  in Settings.
- `play` is the Google Play build. It leaves the support links out, since Play
  does not allow pointing to outside payments.

Both share the application id `com.k1.pitcher`, so one signing key covers every
channel.

## Reproducible builds

The `fdroid` release build is reproducible. F-Droid rebuilds it from the tagged
source, unsigned, and checks that the signature from the GitHub release fits its
build byte for byte, then ships the GitHub-signed APK. To check it yourself:

```
./gradlew :app:assembleFdroidRelease -Punsigned
apksigcopier compare pitcher-android-vX.Y.Z.apk --unsigned \
  app/build/outputs/apk/fdroid/release/app-fdroid-release-unsigned.apk
```

`-Punsigned` leaves the release unsigned instead of falling back to the debug key.
The F-Droid recipe lives in `fdroid/com.k1.pitcher.yml` for reference.

## Signing and releases

Release builds read these environment variables. Without them the debug key is
used, so local release builds still install.

```
PITCHER_KEYSTORE
PITCHER_KEYSTORE_PASSWORD
PITCHER_KEY_ALIAS
PITCHER_KEY_PASSWORD
```

`.github/workflows/android-release.yml` builds the release APK on `v*` tags and
attaches it to a GitHub Release. It needs the repository secrets
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`. Tagged
releases refuse to build without them, so every release is signed with the same
key and updates in place.

## Screenshots

Screenshots are rendered from the real Compose UI with Roborazzi, no device
needed:

```
./gradlew :app:testFdroidDebugUnitTest -Proborazzi.test.record=true --tests "*ScreenshotTest"
```

They land in `app/build/screenshots/`. The published copies live in
`../fastlane/metadata/android/en-US/images/phoneScreenshots/` and
`../site/assets/android/`. Copy them over after regenerating.

## Store listing

The listing text (title, short and full description, per-version changelogs) is
in `../fastlane/metadata/android/en-US/` at the repo root, where F-Droid reads it.
