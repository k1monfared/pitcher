# Pitcher for Android — Plan (v1)

Independent native Android app. No downloading, no streaming, no server: the user
picks an audio or video file already on the device and gets the full pitcher
workflow (pitch lab, tuner, variant shelf, bookmarks, loop, speed, export) with a
clean multi-screen UI. Fully offline, fully FOSS, F-Droid friendly.

## Goals

- Open audio or video from storage or the Android share sheet. Video is never
  kept: the audio track is extracted on import and the video bytes are dropped.
- Everything the web app does: transpose by cents/semitones with tempo preserved,
  single-note tuner plus manual note/Hz entry, variant shelf (sorted, named,
  keep-all), bookmarks, section loop, playback speed, per-file and bulk export.
- Precise seeking with gestures plus a zoomable waveform.
- Keep the screen on while in use; keep playing in the background optionally.

## Non-goals (v1)

- No built-in YouTube/SoundCloud/Spotify downloading: the app bundles no downloader
  and has no network permission (see `docs/LEGALITY.md`). Online sources are
  supported by sharing a downloaded file into pitcher from any other app, or by
  opening it with pitcher from a file manager.
- No polyphonic transcription (same as web v1: monophonic tuner).

## Stack

| Piece | Choice | Why |
|---|---|---|
| Language/UI | Kotlin + Jetpack Compose (Material 3) | Native, no bridge, best gesture support |
| Tuner | Kotlin port of the desktop YIN (`core/Tuner.kt`) | Exact parity with the Rust `tuner.rs`, tested against the same vectors, no extra dependency. TarsosDSP was the original choice but is not on Maven Central (only third-party forks), so a port is safer |
| Live pitch/tempo | Media3 ExoPlayer `PlaybackParameters(speed, pitch)` (Sonic) | Real-time pitch at fixed tempo and tempo at fixed pitch, no extra dependency, no render |
| Offline render | In-app WSOLA + resample (`core/PitchShifter.kt`) | Dependency-free, tested with the YIN tuner, used for exported files |
| Export formats | WAV, MP3 (jump3r/LAME), M4A (MediaCodec AAC), Opus (MediaCodec OGG) | WAV is lossless; FLAC is not offered because there is no usable pure-JVM encoder and MediaMuxer cannot container raw FLAC |
| Playback | Media3 ExoPlayer + `MediaSessionService` | Background play and lock-screen controls; controller in the ViewModel |
| Storage | Hand-rolled SQLite (`ShelfRepository`) | Same tables as the server; avoids a KSP annotation processor and mirrors the server SQL directly |
| Video sources | MediaExtractor + MediaMuxer | Import extracts the audio track to an audio-only file, no re-encode, no video kept |
| Waveform | Custom Compose Canvas | Full control of zoom/pan/markers, no chart dependency |
| Background | Foreground service + Media3 notification (optional toggle) | Playback survives screen-off when the user wants it |
| Screen-on | `setKeepScreenOn(true)` on player screens | Explicit requirement, one line per screen |

Why not reuse the Rust core via JNI: the core shells out to ffmpeg, which does not
exist on Android, so the engine half would need replacing anyway. What we do reuse
is the *specification*: cents-internal math, YIN behavior, and the shelf schema.
Port `notes.rs` to Kotlin with the same unit-test vectors so the two apps agree
exactly (C#4 = 277.18 Hz, interval math, fractional semitones).

Why not Rubber Band via NDK for v1: best quality, but the NDK build is stale
upstream and the JNI surface would be hand-rolled. TarsosDSP ships today with no
native code. Kept renders on the server stay Rubber Band; the phone renders with
TarsosDSP and says so in the UI.

## Screens (one job each, bottom nav)

1. **Library** — track list (title, artist, variant count), import from storage,
   rename/delete with the same file rules as the CLI (never delete user files,
   only our renders). Search later.
2. **Player** — waveform, transport, speed selector, bookmarks list, loop. This is
   the precision screen (see Gestures).
3. **Pitch Lab** — vertical fader (absolute cents from original, same as web),
   semitone stepper is replaced by preset chips (-12/-7/-5/+5/+7/+12, custom via
   keyboard input), formant toggle where the engine supports it, snap readout,
   "keep this pitch" with the same render summary line as web.
4. **Tuner** — detect-at-playhead, manual note/Hz, target note/Hz with two-way
   sync, live interval, "move fader here" (disabled when already there).
5. **Pitches** — the shelf: original card plus variants sorted by shift, names,
   per-card download is not needed on-device (files already are), instead:
   share/export each, export-all zip, delete.
6. **Export/Share** — format picker, per-file and bulk share via system sheet,
   save to Music/ via MediaStore.
7. **Settings** — engine quality, default formats, keep-screen-on toggle,
   background-play toggle, storage usage with a clear-cache (transcode/renders)
   action.

## Precise seeking (explicit requirement)

- Waveform supports pinch-zoom (anchored on playhead), drag-pan (breaks follow),
  recenter chip (Google-maps style follow, same as web), tap-to-seek with
  marker snap.
- Scrub mode: long-press the waveform for a magnified scrubber (slower finger
  travel = finer control), plus dedicated `-1s`/`+1s` and `-5s`/`+5s` steppers
  and a frame-step mode (one audio frame per tap) for sample-exact placement.
- Keyboard/d-pad where present mirrors the web shortcuts; TalkBack labels on
  everything interactive.
- All seeks preserve the selection model from web: switching pitches never moves
  the playhead (regression-tested after the web fix in `6179d80`).

## Data

Room entities mirror the server schema (`tracks`, `variants` with nullable names,
`bookmarks`), plus a `renders` cache table. Variant dedupe rule is identical:
same pitch + settings returns the existing row. Filenames follow the same rule:
`{song} - {pitch name}.{ext}`, fallback `{song} - pitch {cents}.{ext}`.

## Permissions

- `READ_MEDIA_AUDIO`, `READ_MEDIA_VIDEO` (scoped, no broad storage).
- `POST_NOTIFICATIONS` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` only if background
  play is enabled.
- No `INTERNET` permission: proves offline-only, helps F-Droid review.

## Testing

- JUnit: note math (shared vectors with `notes.rs`), interval math, filename
  rules, dedupe queries (in-memory Room), bookmark ordering.
- Compose UI tests: fader absolute behavior (select -600 shows -600, no
  compounding), keep-everything export contents, rename flows.
- Manual device matrix: one low-RAM device (DSP buffer sizing) plus the usual
  emulator ABIs; audio latency measured, not guessed.

## Milestones

1. Shell: nav, theme, note math, SQLite shelf. Done.
2. Import (audio only), playback, waveform, gestures, screen-on, background play. Done.
3. YIN tuner (Kotlin port) + note math parity with the Rust core. Done.
4. Pitch Lab with live Sonic preview, keep/dedupe, variants shelf, touch scrub fader. Done.
5. Speed selector, A/B loop, export/share (offline WSOLA WAV). Done.
6. Polish: settings/storage screen, track rename, share-sheet intake, release
   signing + GitHub Releases workflow. Done.
7. Export formats (WAV/MP3/M4A/Opus), loop-only export, stereo with mono
   fallback. Done.
8. Release prep: adaptive icon, Roborazzi screenshots, fastlane metadata,
   changelog, version 1.0.0, GitHub Releases workflow. Done.
9. Follow-ups: FLAC (blocked on a container-capable encoder), screenshots in
   more languages.

## Decisions (resolved)

- Min SDK 29, target/compile 36.
- Background playback on by default (MediaSessionService).
- Release channel: GitHub Releases, sideloaded APK.
- Online sources: share-sheet intake only; no bundled downloader, no network
  permission.
