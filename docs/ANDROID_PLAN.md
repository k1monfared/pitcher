# Pitcher for Android — Plan (v1)

Independent native Android app. No downloading, no streaming, no server: the user
picks an audio or video file already on the device and gets the full pitcher
workflow (pitch lab, tuner, variant shelf, bookmarks, loop, speed, export) with a
clean multi-screen UI. Fully offline, fully FOSS, F-Droid friendly.

## Goals

- Open audio/video from storage or the Android share sheet. Video works by
  extracting its audio track; export can optionally mux the shifted audio back
  with the original video.
- Everything the web app does: transpose by cents/semitones with tempo preserved,
  single-note tuner plus manual note/Hz entry, variant shelf (sorted, named,
  keep-all), bookmarks, section loop, playback speed, per-file and bulk export.
- Precise seeking with gestures plus a zoomable waveform.
- Keep the screen on while in use; keep playing in the background optionally.

## Non-goals (v1)

- No YouTube/SoundCloud/Spotify importing. If downloading ever comes back it is a
  separate milestone with its own legal review (see `docs/LEGALITY.md`).
- No account, no cloud, no analytics, no network permission at all.
- No polyphonic transcription (same as web v1: monophonic tuner).

## Stack

| Piece | Choice | Why |
|---|---|---|
| Language/UI | Kotlin + Jetpack Compose (Material 3) | Native, no bridge, best gesture support |
| DSP | TarsosDSP (pure JVM) | YIN/FastYin tuner plus WSOLA+resample pitch shifter, Android audio I/O included, zero NDK, maintained (releases through 2025) |
| DSP upgrade path | SoundTouch via JNI (later) | Better quality, proven realtime, but NDK/CMake/ABI matrix; hidden behind a `PitchEngine` interface so v1 ships without it |
| Playback | Media3 (ExoPlayer) for plain play; TarsosDSP pipeline for shifted/speed preview | Media3 for the exact-file path, DSP chain only when pitch or tempo differs |
| Storage | Room (SQLite) + app-private files | Same tables as the server: tracks, variants, bookmarks; keep-all semantics |
| Video | MediaExtractor (audio out), MediaMuxer (shifted audio back in) | Audio-only export by default, video-preserving export as an option |
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

1. Shell: nav, theme, Room schema + DAOs matching the server (tests first).
2. Import + plain playback + waveform + gestures + screen-on (no DSP yet).
3. TarsosDSP tuner + note math parity with the Rust core.
4. Pitch Lab with live preview + keep/render pipeline + variants UI.
5. Bookmarks, loop, speed, export/share, video mux-back option.
6. Polish: storage screen, background play toggle, F-Droid metadata, screenshots.

## Open questions

- Min SDK 26 vs 29 (media APIs are nicer on 29+; recommend 29, ~95%+ of devices).
- Whether background play defaults on or off.
- F-Droid vs Play release first (recommend F-Droid; no proprietary SDKs either way).
