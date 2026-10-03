# Pitcher — Implementation Plan (v1)

## Goal

Transpose audio by an arbitrary pitch interval while preserving tempo, with optional
formant preservation, across a Linux CLI and a local web UI. Keep an original plus a
shelf of pitch variants per source. Detect the exact note at a point in the audio with
aubio, or type a note/frequency manually, then move it by any amount.

## Stack

| Piece | Choice | Why |
|---|---|---|
| Shift engine | `librubberband` (via FFI) | Tempo-independent, formant preservation, best "not annoying" quality |
| Shift fallback | `ffmpeg -af rubberband` | Already built with rubberband here, zero-link option |
| Audio decode/encode | `ffmpeg` / `symphonia` (Rust, decode) | Broad format coverage |
| Tuner | `aubio` (`libaubio`, C) via FFI + `aubiopitch` CLI fallback | apt-installable, fast, monophonic |
| Core + CLI | Rust (`cargo`/`rustc` present) | Single static binary, reusable in Android later |
| Shelf storage | SQLite (`rusqlite`) | Source, variants, favorites, metadata |
| Web server | Rust `axum` (thin REST) | One language for server, no Python runtime needed |
| Web UI | Svelte 5 + Vite + TypeScript | Instant slider/playback, custom canvas |
| Live audition | `rubberband-wasm` AudioWorklet | Zero-latency in-browser preview, same algorithm |
| Importer | `yt-dlp` | YouTube/SoundCloud (ToS note documented) |

Python/ML tuners (torchcrepe, Basic Pitch) are explicitly deferred past v1.

## Repo layout (monorepo)

```
pitcher/
  AGENTS.md                 # source of truth for instructions
  CLAUDE.md                 # wrapper: "@AGENTS.md"
  Cargo.toml                # workspace
  crates/
    pitcher-core/           # engine, tuning, storage, note math
      src/
        engine.rs           # rubberband + ffmpeg backends
        tuner.rs            # aubio FFI, detect at time, detect range
        notes.rs            # hz <-> midi <-> name <-> cents, intervals
        shelf.rs            # sqlite: tracks, variants
        model.rs            # types: Track, Variant, ShiftRequest
        import.rs           # yt-dlp wrapper
        error.rs
    pitcher-cli/            # binary "pitcher"
      src/main.rs
      src/cmd/{pitch,detect,add,try,explore,shelf,export}.rs
  server/                   # axum REST over pitcher-core
    src/main.rs
  web/                      # Svelte 5 + Vite + TS
    src/lib/{cents.ts,api.ts,notes.ts}
    src/components/{PitchFader.svelte,Waveform.svelte,LoopRegion.svelte,
                    NoteTuner.svelte,VariantShelf.svelte,Transport.svelte}
    src/workers/rubberband-processor.js
    public/rubberband.wasm
  docs/
    PLAN.md                 # this file
    LEGALITY.md             # Spotify/YouTube/SoundCloud ToS notes
  data/                     # SQLite db + rendered variants (gitignored)
```

## Note and interval math (`notes.rs`)

All shift amounts stored internally as **cents** (integer). Conversions:

- Ratio: $r = 2^{c/1200}$
- Hz to MIDI: $m = 69 + 12\log_2(f/440)$
- Hz to note + deviation: name from `round(m)`, cents off $= 100(m - \text{round}(m))$
- Interval between two freqs: $c = 1200\log_2(f_\text{target}/f_\text{source})$
- Note names use scientific pitch notation (C#4). Microtonal targets allowed: any cents
  offset, e.g. C#4 + 37 cents.
- Presets: semitones, and fractional amounts (1/8, 1/16, 5 + 1/16 semitones →
  12.5, 6.25, 506.25 cents).

## Core engine (`engine.rs`)

```rust
pub struct ShiftRequest {
  pub input: PathBuf,
  pub output: PathBuf,
  pub cents: i32,
  pub formant: bool,          // false = shifted, true = preserved
  pub engine: Engine,         // Speed | Quality | Consistency  (RB engines)
  pub pitch_quality: PitchQ,  // speed | quality | consistency
  pub section: Option<(f64,f64)>, // seconds, loop-render a slice
  pub output_format: Option<String>,
}
pub enum Backend { RubberBandLinked, Ffmpeg }
```

- Prefer linked `librubberband`; fall back to
  `ffmpeg -af rubberband=pitch=<r>:formant=preserved:...` when not linked.
- Section render: trim with ffmpeg first, shift, then export as a clip.
- Default flags for kept exports: `engine=finer`, `formant=preserved`,
  `pitchq=quality`. Interactive preview speed is handled by WASM.

## Tuner (`tuner.rs`)

- `detect_at(path, t, window_ms=250) -> PitchReading { hz, midi_float, note, cents_off, confidence }`
- `detect_range(path, from, to) -> Vec<Frame>` for the waveform overlay.
- aubio via FFI (`new_aubio_pitch("yinfft", 2048, 512, sr)`), default method `yinfft`;
  expose `yin`/`yinfast` as options.
- Silence guard: reuse aubio's silence detection so quiet frames return no note.
- CLI fallback: shell to `aubiopitch` if the link is unavailable.
- Manual override: `PitchSource::Manual { hz }` or `Manual { note, cents }`. In the UI
  the tuner result is editable: type a note (C#4), type a frequency (277.18), or nudge
  by cents, and the shifter uses that as the source pitch.

## Storage (`shelf.rs`)

```sql
tracks(id, source_path, source_kind, source_url, title, artist,
       duration_s, sample_rate, created_at)
variants(id, track_id, cents, formant, engine, pitch_quality,
         section_start, section_end, output_path, output_format,
         src_note, src_hz, target_note, target_hz,
         favorite, created_at)
```

Original audio is a row in `tracks`; each transpose is a `variants` row. `favorite` is
the keep-1-or-2 mechanism. Cascade delete by `track_id`.

## CLI (`pitcher`)

```
pitcher add <file|url> [--title --artist]      # import local or yt-dlp
pitcher list                                   # shelf: tracks + variant counts
pitcher detect <track> --at 12.3s [--method yinfft]
pitcher detect <track> --range 12.0 13.0
pitcher pitch <in> <out> --cents -100 [--formant] [--engine finer]
             [--from 30 --to 45] [--format mp3]
pitcher try <track>                            # REPL: type cents, audition, keep
pitcher explore <track> --grid 100 --span 300  # shelf of candidates, star one
pitcher shelf star <variant_id> | prune <track> | export <track> <dir>
```

Interactive `try`: read cents from stdin, render preview, play via `ffplay`/`pw-play`,
`k` to keep (writes a variant row), `q` to quit.

## Server + Web UI

REST (axum), all over the same core:

```
POST /import            (file path or url)
GET  /tracks            GET /tracks/:id
POST /tracks/:id/shift  -> renders kept variant (server-side, exact)
POST /preview           (optional server render when WASM unavailable)
GET  /media/:variant    (audio bytes for A/B)
POST /detect            {track, at} -> PitchReading
POST /variants/:id/star DELETE /variants/:id
```

Svelte components:

- `PitchFader.svelte` — vertical cents slider, range ±1200, snap options off / 100 / 10 / 1
  cents, keyboard up/down nudges. Live-updates the worklet `setPitchSemitones(cents/100)`
  with no server round trip.
- `Waveform.svelte` — canvas, waveform peaks, detected-pitch overlay, click to set playhead.
- `LoopRegion.svelte` — drag start/end, loop toggle. Passes section into render requests.
- `NoteTuner.svelte` — detected note + cents off, editable note and Hz, "use as source"
  toggle, target note picker, live interval readout.
- `Transport.svelte` — play/pause, A/B original vs shifted, seek.
- `VariantShelf.svelte` — grid of variants, star/keep, delete, export.

Live audition: `public/rubberband-processor.js` + `rubberband.wasm` from `rubberband-wasm`,
loaded into an `AudioWorkletNode`. The worklet owns the decoded `AudioBuffer`; the store
drives pitch/tempo/loop from the UI. Server render is used only when you export,
guaranteeing the file matches the CLI engine settings.

Smoothness details: pointer events with `setPointerCapture` on the fader,
`requestAnimationFrame`-throttled parameter writes, no reactive round trip through the
server, preloaded decoded buffers so A/B is instant.

## Importer + legality

`import.rs` shells to `yt-dlp` for YouTube/SoundCloud URLs. Spotify links resolve metadata
only (title/artist), audio must come from elsewhere. `docs/LEGALITY.md` states plainly that
ripping is against platform ToS and possibly copyright, and that local files are the clean
path. The tool does not circumvent DRM.

## Build and run

- `cargo build --release` → `pitcher` binary; workspace also builds `server`.
- `npm install && npm run build` in `web/`, server serves the static bundle.
- Launcher script scans from port 7373 upward for a free port, prints localhost and LAN URLs.
- System deps: `rubberband-cli librubberband-dev aubio-tools libaubio-dev`. All FOSS.

## Tests

- `notes.rs`: unit tests for Hz/MIDI/name/cents round trips and interval math (C#4 to G5,
  ±1/16 semitone, 5+1/16 semitone = 506.25 cents).
- `engine.rs`: golden render of a known sine at -100 cents, assert measured spectrum peak
  shifted by expected ratio via aubio.
- `tuner.rs`: synthesized 440 Hz and 277.18 Hz sine → correct note and <5 cents confidence.
- CLI: integration test of `add`, `pitch`, `detect`, `shelf`.
- Web: a Playwright smoke test that drags the fader and asserts the worklet pitch parameter
  changes.

## Deferred (post-v1)

- torchcrepe accurate tuner, Basic Pitch polyphonic mode.
- Android Kotlin app reusing `pitcher-core` via JNI.
- `signalsmith-stretch` alternative engine comparison.

## Milestone order

1. Workspace skeleton, `notes.rs` + tests, `engine.rs` with ffmpeg backend.
2. CLI `pitch`/`detect`/`try` end to end (ffmpeg backend first, then librubberband link).
3. SQLite shelf + `add`/`shelf`/`explore`.
4. axum server + Svelte UI with fader, waveform, loop, runner.
5. WASM live audition worklet wired into the fader.
6. yt-dlp importer + `LEGALITY.md`.
7. Polish: A/B, keyboard nudges, snap modes, port-scan launcher.
