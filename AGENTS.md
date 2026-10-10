# AGENTS.md

Pitcher is a FOSS audio pitch-shifting toolkit: transpose audio by any interval while
preserving tempo (and optionally formants), detect the exact note at a point in a track,
and keep a shelf of pitch variants per source. Linux CLI + local web UI, monorepo.

## Full plan

The complete v1 plan lives in [docs/PLAN.md](docs/PLAN.md). Read it before making design
decisions. Summary of the chosen stack:

- **Shift engine:** `librubberband` via FFI, with `ffmpeg -af rubberband` as a fallback.
- **Tuner:** `aubio` (C library) via FFI, monophonic, `yinfft` by default. `aubiopitch`
  CLI fallback.
- **Core + CLI:** Rust. Single static binary, reusable from Android later.
- **Shelf storage:** SQLite via `rusqlite`.
- **Web server:** Rust `axum`. **Web UI:** Svelte 5 + Vite + TypeScript.
- **Live audition:** `soundtouchjs` in the browser (pitch + tempo preview).
  Kept files are always rendered server-side with Rubber Band.
- **Importer:** `yt-dlp` for YouTube/SoundCloud. See [docs/LEGALITY.md](docs/LEGALITY.md).
- torchcrepe and Basic Pitch are deferred past v1.
- The Android app is planned separately in [docs/ANDROID_PLAN.md](docs/ANDROID_PLAN.md):
  native Kotlin + Compose, offline-only, no downloaders.
- The Android v2 redesign (touch-first, full-screen pitch gesture) is planned in
  [docs/ANDROID_V2_PLAN.md](docs/ANDROID_V2_PLAN.md) and built in `ui/modern/`.
- The Android 2.5 rework (rebuilt gesture, loops and bookmarks timeline, unified
  render sheet, tuner modal) is in [docs/ANDROID_V3_PLAN.md](docs/ANDROID_V3_PLAN.md).
  The classic interface was removed; the modern Studio is the only one.
- Android shifts with Media3's Sonic for both playback and saved files, so files
  match the preview. Kept pitches pre-render to a cached WAV in the background.
  Release notes live in `android/CHANGELOG.md` and the store listing in
  `fastlane/` at the repo root, where F-Droid looks for it.

## Layout

```
crates/pitcher-core/   engine, tuner, note math, sqlite shelf, import, model
crates/pitcher-cli/    the `pitcher` binary
server/                axum REST server over pitcher-core
web/                   Svelte 5 + Vite + TS frontend (live preview via soundtouchjs)
site/                  static project site (GitHub Pages): landing + web UI guide
docs/PLAN.md           the plan
docs/ANDROID_PLAN.md   the Android app plan
docs/ANDROID_V2_PLAN.md  the Android v2 redesign plan
docs/ANDROID_V3_PLAN.md  the Android 2.5 gesture/timeline/render plan
docs/LEGALITY.md       source/ToS notes
data/                  sqlite db + rendered variants (gitignored)
android/core/          pure-Kotlin note math (ported from notes.rs), tested on JVM
android/app/           native Android app (Kotlin + Compose), offline only
fastlane/              store listing text and screenshots (F-Droid and Play)
```

## Conventions

- All pitch amounts are **cents** internally (integer). Conversions in `notes.rs`:
  ratio $r = 2^{c/1200}$; Hz to MIDI $m = 69 + 12\log_2(f/440)$; interval
  $c = 1200\log_2(f_\text{target}/f_\text{source})$. Note names use scientific pitch
  notation (C#4).
- Prefer linked `librubberband`; use the ffmpeg backend only when the link is absent.
- Do not add comments unless they carry information the code cannot.
- No emojis in code, UI, or docs.

## Development workflow (important)

Test-driven, commit often:

1. Write tests **before** building the feature.
2. Commit the tests.
3. Build until tests pass.
4. Commit the passing implementation.
5. Run the full test suite before every commit.

Never commit with failing tests.

## Commands

```
cargo build --release            # builds pitcher + server
cargo test                       # full Rust test suite
cargo test -p pitcher-core       # one crate
cargo clippy --all-targets       # lint
cargo fmt                        # format
cd web && npm install && npm run build   # frontend
cd web && npm test               # frontend tests
cd android && ./gradlew :core:test        # Android core note-math tests
cd android && ./gradlew :app:testFdroidDebugUnitTest  # Android app unit + screenshot tests
cd android && ./gradlew :app:assembleFdroidDebug  # build debug APK
cd android && ./gradlew :app:assembleFdroidRelease # minified release APK (GitHub and F-Droid)
cd android && ./gradlew :app:testFdroidDebugUnitTest -Proborazzi.test.record=true --tests "*ScreenshotTest"  # render screenshots
```

Android release signing reads `PITCHER_KEYSTORE`, `PITCHER_KEYSTORE_PASSWORD`,
`PITCHER_KEY_ALIAS`, `PITCHER_KEY_PASSWORD`; without them it falls back to the
debug key. `.github/workflows/android-release.yml` builds and attaches the APK
to a GitHub Release on `v*` tags.

Android builds need `android/local.properties` with `sdk.dir=/path/to/Android/Sdk`
(gitignored). Java 17+ and the Android SDK (platform 36, build-tools) are required.

## System dependencies

```
rubberband-cli librubberband-dev aubio-tools libaubio-dev ffmpeg yt-dlp
```

All FOSS.

## Local servers

When launching the dev or production server, do not fail on a busy port. Start from 7373,
scan upward for the next free port, bind it, and report both the localhost URL and the LAN
URL. Build this into the launcher, not as a one-off.
