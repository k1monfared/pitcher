# pitcher

FOSS audio pitch-shifting toolkit. Transpose audio by any interval while preserving tempo,
optionally preserve formants, detect the exact note at a point in a track, and keep a shelf
of pitch variants per source. Linux CLI plus a local web UI, and a separate offline Android app.

Project site: <https://k1monfared.com/pitcher/> (web UI guide: <https://k1monfared.com/pitcher/web/>)

## What it does

- Shift any audio (local files, or imported from YouTube/SoundCloud) by a pitch interval in
  cents, tempo unchanged. All amounts are cents internally: C#4 to C4 is -100 cents.
- Preserve formants (`--formant`) so vocals and acoustic instruments do not sound warbly.
- Detect the note at a moment in the track (pure-Rust YIN), or type a note/frequency manually.
- Move a detected note to a target note, including microtonal offsets (C#4 + 37 cents).
- Keep an original plus every pitch variant you rendered, star the keepers, prune the rest.

See [docs/PLAN.md](docs/PLAN.md) for the design and [docs/LEGALITY.md](docs/LEGALITY.md) for
source and terms notes.

## Requirements

```
rubberband-cli librubberband-dev aubio-tools libaubio-dev ffmpeg yt-dlp
```

Only `ffmpeg` (with the rubberband filter) is required at runtime for shifting, plus
`ffmpeg`/`ffprobe` for decoding. `yt-dlp` is needed only for URL import. The Rust tuner is
built in, so aubio is optional. All FOSS.

## CLI

```
cargo build --release

./target/release/pitcher pitch in.wav out.wav --cents -100 --formant
./target/release/pitcher pitch in.wav out.wav --to-note C4 --from-note "C#4"
./target/release/pitcher detect in.wav --at 12.3
./target/release/pitcher note --hz 277.18
./target/release/pitcher interval C#4 C4
./target/release/pitcher interval --source-hz 277.18 --target-note G5
./target/release/pitcher add in.wav --title "My Track"
./target/release/pitcher explore 1 --offset -200 --span 400 --step 50
./target/release/pitcher try 1
./target/release/pitcher shelf star 3
./target/release/pitcher shelf export 1 ./kept
./target/release/pitcher rename 1 --title "New Title" --artist "Me"
./target/release/pitcher delete 1            # asks first; --yes to skip
```

## Web UI

```
./scripts/pitcher-web.sh
```

Builds the frontend if needed, builds the server, and serves on the first free port from
7373. Prints the localhost and LAN URLs. The UI has a vertical pitch fader (live audition,
snap modes, keyboard nudges), a canvas waveform with click-to-seek and shift-drag looping,
a note tuner with manual override, and a variant shelf.

## Android

A separate native Kotlin + Jetpack Compose app, fully offline (no network
permission, no bundled downloader). The touch-first Studio is the whole app. A
short guided tour runs on first launch and can be replayed from Settings. See
[android/README.md](android/README.md) and
[docs/ANDROID_V3_PLAN.md](docs/ANDROID_V3_PLAN.md).

## Tests

```
cargo test                    # Rust: notes, engine, tuner, shelf, CLI, server, ports
cd web && npm install && npm test   # TypeScript: notes, fader, audio, api
cd android && ./gradlew :core:test :app:testDebugUnitTest   # Kotlin core + app
```

## Layout

```
crates/pitcher-core/   engine, tuner, note math, sqlite shelf, import, model
crates/pitcher-cli/    the `pitcher` binary
server/                axum REST server over pitcher-core (serves web/dist)
web/                   Svelte 5 + Vite + TS frontend (live preview via soundtouchjs)
site/                  static project site (GitHub Pages): landing + web UI guide
docs/                  plan, Android plans, and legality notes
data/                  sqlite db + rendered variants (gitignored)
android/core/          pure-Kotlin note math, tested on the JVM
android/app/           native Android app (Kotlin + Compose), offline only
android/fastlane/      store listing metadata and screenshots
```
