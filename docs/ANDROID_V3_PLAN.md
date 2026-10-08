# pitcher for Android - Studio, timeline, and render rewrite (release 2.5)

Status: shipped in 2.5.0. This pulls together the decisions from the planning
discussion and supersedes the v2 plan wherever they differ. The v2 "classic"
interface is removed in this release.

## Scope

- Remove the classic interface and the interface-style switch.
- Rebuild the pitch gesture from scratch.
- Rebuild the song chart: precise dragging, multiple loops, bookmarks.
- Port the tuner into a modern modal.
- Unify render and export into one sheet.
- Settings: default save folder, haptics toggle, floating gear.

Version 2.5 (versionCode bump from 2.0.1).

## Removing the classic interface

- Delete `android/app/.../ui/classic/`, the `UiStyle` preference and switch, and
  the Pitches/Player/Pitch Lab/Tuner classic screens.
- The modern Studio already covers playback, waveform, speed, loop, bookmarks,
  the pitch shelf, and render.
- Remove classic references from `docs/ANDROID_PLAN.md`, `docs/ANDROID_V2_PLAN.md`,
  `AGENTS.md`, `README.md`, and `android/README.md`.
- The classic Tuner is not dropped. It is ported into a modern modal, described
  below.

## Pitch gesture (rebuild)

Vertical only, no absolute scale, no horizontal gear axis.

- Track a continuous value. Each pointer move adds `dyPx * centsPerPx`. Never
  feed the snapped value back into the accumulator (this is the current bug).
- `centsPerPx` is driven by pointer speed (encoder acceleration, the DAW knob
  model). Slow movement is fine, fast is coarse. 7 gears from 1 cent to 200
  cents, chosen by speed.
- Precision ramp at the start of every touch: the first ~10dp of movement are
  finer than 1:1, then it latches to 1:1 for the rest of the gesture. Distance is
  `min(10.dp, 0.10 * padHeight)`.
- Momentum on release: convert the release velocity to cents per second and let
  it coast and decay (the Android spline fling model). Any touch down grabs and
  stops it. Soft stop at +/-1200, reversal always allowed.
- Display integers. Apply the continuous value.
- Taps: double-tap the readout resets to 0. Single-tap the readout opens exact
  entry. A tap elsewhere on the pad does nothing.

Snap and haptics:

- One `snap` label that lights when on. When on, it rounds the final value on a
  dual magnetic grid: light at 20 cents, strong at 100 cents (semitones), with a
  haptic tick at each crossing.
- Haptics behind a Settings toggle, default on.

The math lives in `:core` (a rewrite of `PitchGesture`) with unit tests for
monotonic accumulation, ramp, gear changes, and momentum decay.

## Song chart and timeline

One reusable "precise drag" used by several handles:

- Long-press a handle, then drag horizontally. The first ~10dp are fine (ramp),
  then 1:1, and the finger/value gap closes smoothly so it never jumps.
- A floating timestamp follows just above the thumb, showing only the timestamp.
- Release commits. The playhead seek is applied on release, not during the drag.

What the long-press grabs, by hit test:

- Near a bookmark head: move that bookmark.
- Near a loop edge: move that edge, clamped so a loop cannot cross its own other
  edge or a neighbouring loop (loops stay non-overlapping).
- Otherwise on the chart: scrub the playhead.

Chart interactions:

- Tap: seek, snapping to a nearby bookmark.
- Plain drag: pan when zoomed in. When zoomed out, plain drag scrubs (there is
  nothing to pan).
- Long-press + drag: precise drag (above).
- Pinch: zoom.
- The loop lane above the waveform is wider (about 40dp). Drag empty lane space
  to create a loop. Long-press an edge to fine-tune.
- Bookmark heads are small pins on the top edge of the chart, one per bookmark.
- Names (when present) and timestamps render small and out of the way.

## Loops and bookmarks

Data:

- Loop: `{id, startMs, endMs, name?, enabled, createdAt}`. Non-overlapping,
  sorted by start, not reorderable this version.
- Bookmark: `{id, tMs, name?, createdAt}`. Independent of loops.

Libraries in a bottom drawer:

- Loops list: each row has name (or timestamp), the timestamp range, an
  on/off toggle, and long-press to rename or delete. Tap selects the loop, which
  is what "this loop" mode uses.
- Bookmarks list: each row has name (or timestamp), tap to seek, long-press to
  rename or delete.
- New items default to a timestamp name until renamed.

Loop mode button (Spotify repeat pattern), three states that cycle on tap:

- Whole song (no loops): dim loop icon. Plays straight through.
- All loops: accent loop icon. Plays enabled loops in order, repeating.
- This loop: accent loop icon with a "1". Repeats the selected enabled loop.

Per-loop on/off is separate from the master mode. Disabled loops are excluded
from playback and from export.

## Render sheet (unified)

Entry points: tapping the `+ render` card and long-pressing a saved pitch. The
bottom `Export this pitch` button and the word "export" are gone.

Sheet contents, top to bottom:

- Destination address as text, clickable. Opens a small modal to set the folder
  for the current song, with a hint row that links to the default in Settings.
- File name field showing the full `song - artist - pitch` plus the extension as
  a separate, non-editable token. The name text is editable.
- Format control: an always-open vertical strip of WAV / MP3 / M4A / Opus,
  tapped or slid. The filename's extension token reflects it.
- Loop-only option, whole row clickable. With multiple loops, this means "render
  the enabled loops concatenated".
- Primary: `Save file`. Secondary: `Share`. Status line with `Cancel`.

## Multiple-loop render

- All enabled loops, chronological, hard cuts, one file.
- Implement at the PCM level: decode each loop segment, shift each, concatenate
  the PCM, then encode once. This gives a clean sample-accurate cut instead of
  stitching encoded files.
- No enabled loops: render the whole track.
- Master loop mode does not change what gets exported. Export is always the
  enabled loops concatenated, or the whole track.

## Save folder

- SAF folder picker (`ACTION_OPEN_DOCUMENT_TREE`), the system folder chooser.
  Persist the granted tree URI.
- Default folder in Settings, per-song override from the render sheet.
- Trade-offs: the displayed path is the folder name or tree, not a raw
  filesystem path, and files written through SAF bypass MediaStore so a media
  scan may be needed for them to show in other apps' libraries.

## Settings and layout

- Settings becomes a floating gear in the top-right corner.
- Add a default save folder row and a haptics toggle.
- `+ save` card becomes `+ render`.

## Tuner modal

Ported from the classic Tuner, out of the way. Entry: long-press the cents
readout. No main-screen button.

- Two notes, a source and a target, each fillable three ways:
  - Detect at the playhead (the existing YIN tuner).
  - Pick a bookmark: the modal lists the song's bookmarks, and picking one seeks
    there and detects.
  - Type a note or a frequency. Notes are case-insensitive (`c#4`, `Db4`,
    `C#4+37`), and a bare number is read as Hz.
- The modal shows the difference between the two: cents and the nearest interval
  name.
- An `Apply to slider` button sets the pitch slider to that cents value, so the
  transposition is one tap.
- The detected reading shows the note name and the cents offset, since a
  detected pitch is rarely exactly on a note.

Persistence, per song:

- Store the last source and target note for the track, across sessions, and let
  them be overwritten any number of times. Only the last pair is kept.
- Backed by new per-track columns (source note, target note) or a small
  per-track row.
- Reopening the modal for a track prefills the last values.

Core change:

- `Notes.noteToHz` rejects a lowercase pitch letter today. Make it
  case-insensitive and cover it with a test.

## Other fixes (this release)

- Double-tap the readout resets (today the readout tap opens exact entry and eats
  the double tap).
- `saving` label no longer wraps or changes the card height.
- The loop-only row is fully clickable.
- Timestamps under the waveform are no longer clipped.
- A separate way to delete or clear a loop, distinct from the master on/off.

## Open items

- Momentum travel distance to tune on device.
- Whether the render sheet's `Share` also renders when nothing is saved yet.
- Tuner entry point: long-press the readout is the proposal, confirm or pick
  another.
