# pitcher for Android v2 — design plan

> Status: shipped and iterated. The touch-first Studio is the interface, and a
> guided tour was added on top of this plan. The v1 multi-screen screens were
> removed in 2.5; see [ANDROID_V3_PLAN.md](ANDROID_V3_PLAN.md). A few details
> changed during the build and are marked inline: Material 3 Expressive was not
> usable, the speed and loop controls took simpler forms, and the view model was
> not split.

A complete visual and interaction redesign of the mobile app. This document is
design-first: it defines the vision, the screens, and the gestures before any
code. v1's engine, storage, playback, and export layers are reused unchanged.

## Vision

A touch-first, visual pitch studio for one song at a time. The pitch is the
hero, the whole screen is the instrument, and import/library move out of the
way. Every touch gets immediate visual and haptic feedback. It should feel like
a modern music app (SoundCloud's waveform, NewPipe's screen gestures,
Instagram's smooth direct manipulation) rather than a form with sliders.

## Principles

1. Gesture first, chrome second. Controls fade; the canvas stays.
2. The whole screen is an instrument. The pitch gesture uses the full surface.
3. Continuous and analog. No discrete steps unless the user asks for them.
4. One primary job per screen. The Studio is where 95 percent of the time goes.
5. Feedback is instant. A HUD, a color shift, a haptic tick, never a silent tap.
6. Calm, dark, expressive motion. Motion comes from Material 3 Expressive.

## Information architecture

The app is one main screen plus sheets and one secondary page. There is no
bottom tab bar on the Studio; it would compete with the canvas.

- **Studio (home)**: the open song. Pitch, playback, speed, loop, pitch shelf,
  export. Everything the user does day to day.
- **Library**: import and browse songs. Opened from the top-left button. Not a
  place the user sits; a place they visit.
- **Export sheet**: format, loop-only, save, share. Pulled up from the Studio.
- **Settings**: a button at the bottom of the Studio (the plan proposed a
  top-right overflow menu): keep screen on, interface style, storage, engine
  info, and replaying the guided tour.

Shipped changes: the separate Pitches sheet was dropped; the shelf is inline on
the Studio. The Library uses a list (not a grid) for now, and the left-edge
swipe was not built.

Rationale: importing happens once per song; experimenting with pitch happens
constantly. So import is demoted and pitch experimentation is the home.

## The Studio screen

Top to bottom:

```
┌──────────────────────────────────────────┐
│ ☰  Nava Sol Darya                   ⋯     │  fading top bar
├──────────────────────────────────────────┤
│                                          │
│                 -600                     │  giant cents, live
│              down a tritone              │  interval in words
│                                          │
│            ( pitch pad )                 │  full gesture surface
│        drag up/down to shift             │  left = fast, right = fine
│                                          │
├──────────────────────────────────────────┤
│  ▁▂▅█▇▅▂▁▂▅█▇▅▂▁▂▅█▇▅▂▁▂▅█▇▅▂▁▂▅█▇▅▂▁▂    │  waveform scrubber
│  ╠═════════ A █████████ B ═══════════╣   │  loop region + handles
├──────────────────────────────────────────┤
│    ⏮      ▶ / ⏸       ⏭     [1.0x] [↻]   │  transport, speed, loop
├──────────────────────────────────────────┤
│  ● orig   ○ -600   ○ +200   ○ D4   +      │  pitch shelf carousel
├──────────────────────────────────────────┤
│              ⌃  pitches / export          │  sheet handle
└──────────────────────────────────────────┘
```

Details:

- **Background**: a generative gradient derived from the audio (or blurred album
  art when present), slowly drifting. The accent hue reflects the current pitch
  (cool for down, warm for up) so the whole screen reads the shift at a glance.
- **Cents readout**: the largest type on screen, animated with a spring when it
  changes. Below it, the interval in words (down a tritone, up a fifth).
- **Pitch pad**: the large region above the waveform. The entire region is the
  gesture surface (see below). No visible track, so it feels like the screen,
  not a widget.
- **Waveform scrubber**: SoundCloud-style. Played portion filled, unplayed dim,
  a bright playhead. Pinch to zoom, drag to pan, tap to seek, and two draggable
  loop handles with a tinted region between them.
- **Transport**: large play/pause, skip back/forward, a speed chip that can be
  dragged and held, and a loop chip that toggles the A/B region.
- **Pitch shelf**: a horizontal carousel of the original plus every saved pitch
  for this song. Selecting one applies it live without moving the playhead.

## The pitch gesture (the centerpiece)

This adapts NewPipe's full-screen vertical gesture (volume/brightness on screen
halves, with a HUD) into a two-axis pitch control where the horizontal position
sets the sensitivity.

**Surface**: the whole pitch pad, and it keeps receiving events even as the
finger moves off it, so the full screen is usable.

**Axes** (sensitivity is relative to where the touch begins):
- Vertical movement changes the pitch. Up raises, down lowers.
- Horizontal offset from the touch-down point sets sensitivity, continuously.
  Moving right of the start is fine, moving left is coarse, and everything
  between is a smooth blend. Starting anywhere works, so there is no edge to
  reach for.

Let $d = x - x_0$ be the horizontal offset from the touch-down point, and let
$D$ be the half-travel that spans the full gear range (for example $0.5W$).
Define $u = \operatorname{clamp}(d / D, -1, 1)$ with a small dead zone around
$u = 0$ for normal sensitivity. Sensitivity in cents per pixel is

$$ s(u) = s_\text{coarse} \left( \frac{s_\text{fine}}{s_\text{coarse}} \right)^{\frac{u + 1}{2}} $$

with, for example, $s_\text{coarse} = 8$ cents/px when the finger is far left
of the start and $s_\text{fine} = 0.15$ cents/px when it is far right. The
exponential blend feels even across the range. The cents delta for a vertical
move $\Delta y$ is

$$ \Delta c = \Delta y \cdot s(u) $$

Consequences on a roughly 1000 px wide, 800 px tall screen:
- Moving the finger left of the start and dragging vertically covers about two
  octaves over the screen height.
- Moving it right of the start covers tens of cents, so a single cent is easy.
- The user can start a drag, then slide left to go fast or right to fine-tune
  mid-gesture, without lifting the finger. This is the key move.

**HUD overlay**: while dragging, an overlay appears over the pad with
- a vertical slider with a handle at the current pitch,
- a scale in semitones (+12, 0, -12) with a highlighted zero,
- the exact value in large type (for example `-600 c`),
- a small label of the current gear (`fine`, `normal`, `coarse`) that updates as
  the finger moves left or right of the touch-down point, so the gear is
  discoverable.

It fades in fast and out about 600 ms after release, using the expressive motion
spec, so it never lingers.

**Haptics**: a light tick at each semitone crossed, a stronger tick at whole
notes (multiples of 100 cents), and a firm tick at 0. This makes the coarse
range feel like a detented hardware control.

**Snap**: an optional snap to semitones (a toggle near the readout). When on,
the HUD shows the note name it will land on.

**Precision and correction**:
- Double-tap the pad resets to 0.
- Long-press opens numeric entry to type an exact cents value.
- The cents readout is tappable for the same numeric entry, and has small +/-
  steppers for one and ten cents.

## Speed control

Listening at different speeds back and forth is a first-class use.

- **Speed chip**: shows the current rate (1x). Shipped behavior: press and slide
  up or down to pick a preset (0.5x to 2x) with a HUD listing the presets, and
  tap it to return to 1x. (The plan proposed a horizontal drag plus a slider.)
- Tempo is independent of pitch (Media3 Sonic), so speed never changes the note.

Hold-to-audition was explored and dropped in favor of the preset picker.

## Loop and repeat a part

- **Loop lane**: shipped behavior: drag the thin lane above the waveform to set A
  and B; the region tints. (The plan proposed draggable handles over the wave.)
- **Loop chip**: taps cycle set A, then set B (which turns the loop on), then
  toggle; long-press clears. The plan's repeat count was not built.
- Loop and speed apply to whichever pitch is selected from the shelf.

## Pitch shelf (per song)

- Horizontal carousel: a card for the original, then a card per saved pitch.
- Each card shows the name (or the cents when unnamed), a tiny waveform
  sparkline, and a badge when a rendered file exists in `Music/pitcher`.
- Tap to select (applies live, keeps the playhead). Long-press for rename,
  share, or delete. A trailing `+` card saves the current pitch.
- The full Pitches page (from the sheet) lists everything with saved-file status
  and per-pitch share/delete, as in v1 but restyled.

## Import and Library

- Import is a button on the Library page (system file picker), plus the existing
  share-sheet intake so a download from another app lands here.
- Library: a searchable grid of song cards with art (or a generated gradient),
  title, artist, and pitch count. Tap to open in the Studio.
- Because importing is rare, it never occupies the home screen.

## Export

A bottom sheet:
- format chips (WAV, MP3, M4A, Opus),
- an "only the A/B loop" toggle,
- a name field (defaults to the `song - artist - pitch` rule),
- two actions: **Save to Music/pitcher** (keeps it in the library) and
  **Share** (system share sheet, rendering first if needed).
- Inline progress and a clear message; a render is saved immediately so it is
  never lost.

## Screen-on

The screen stays on while the app is in the foreground, via the window
keep-screen-on flag in `AppRoot`, gated by a Settings toggle. Note: the modern
interface missed this until 2.0.1; only the classic interface honored it before.

## Visual language

- **Theme**: Material 3 Expressive was the intent. `MaterialExpressiveTheme` and
  `MotionScheme` are still `internal` in `material3` 1.4.0, so the shipped theme
  is a plain `MaterialTheme` (custom dark scheme) with motion curves defined by
  hand in `theme/ModernTheme.kt`.
- **Palette**: deep near-black background, one accent that shifts with the pitch
  (a cool blue when down, a warm amber when up, neutral at 0), a calm green for
  saved state. Text is high contrast, with large display numerals for cents.
- **Surfaces**: translucent, softly blurred HUD and sheets over the canvas
  (blur where the platform supports it, a translucent scrim elsewhere).
- **Motion**: spring physics throughout. The cents readout springs, the HUD
  fades and scales, chips press with a scale, sheets drag with rubber-banding,
  and opening a song can use a shared-element transition from the Library card.
- **Haptics**: semitone detents on the pad, ticks on chips and handles.

## Component inventory

The "modern stack of objects", each a small composable:

- `PitcherTheme` — expressive theme, motion scheme, tokens.
- `StudioScreen` — the home layout.
- `PitchPad` — full-screen gesture surface; owns the drag state.
- `PitchHud` — overlay slider, scale, cents readout, gear label.
- `WaveformScrubber` — SoundCloud-style canvas with zoom, pan, seek, loop.
- `LoopHandles` — draggable A/B handles.
- `TransportBar` — play/pause, skip, speed chip, loop chip.
- `SpeedChip` — drag to change, hold to audition.
- `PitchShelf` and `PitchCard` — the per-song carousel.
- `LibraryScreen` and `SongCard`.
- `PitchesSheet`, `ExportSheet`, `ImportSheet`.
- `FadingTopBar` — appears on tap, fades when idle.
- `CentsReadout`, `Stepper`, `FormatChips`.

## Tech stack

- Kotlin + Jetpack Compose on `material3` 1.4.0. The Expressive theme API is not
  public yet, so the theme is a custom `MaterialTheme`; revisit when the
  Expressive API ships.
- Reuse everything below the UI: Media3 playback and Sonic pitch/tempo,
  `MediaSessionService` background play, the WSOLA renderer, `RenderedStore`
  (MediaStore to `Music/pitcher`), the SQLite shelf, the YIN tuner, and the
  shared note math.
- Gestures with `Modifier.pointerInput` (`awaitEachGesture` for the two-axis
  pad), animation with `androidx.compose.animation`, haptics via the Compose
  haptic feedback API, sheets with `BottomSheetScaffold` and `ModalBottomSheet`.
- No new heavy dependencies. One exception to evaluate: a blur modifier
  (Compose has `Modifier.blur`; use it where the platform supports it).

## Migration from v1

- The engine, storage, playback, and export layers are unchanged; only the UI is
  rebuilt.
- Shipped: the single `PitcherViewModel` was kept and shared by both interface
  styles (the plan proposed splitting it into studio and library view models).
- The pitch gesture math moves into `:core` as a pure, tested function
  (`PitchGesture`, tested in `PitchGestureTest`).

## Milestones

1. Design system and Studio shell: theme, motion, background gradient, giant
   cents readout, transport, and the waveform scrubber (visuals first).
2. Pitch pad: the two-axis gesture, the HUD, haptics, snap, and numeric entry.
   Pure math tested in `:core`.
3. Pitch shelf carousel, save/select, and the per-song library.
4. Speed chip (drag and hold-to-audition) and the loop handles with repeat.
5. Library page, import, and the sheets (pitches, export, import).
6. Polish: expressive motion, shared-element transitions, accessibility,
   screenshots, and on-device tuning of the sensitivity curve.

## Decisions (resolved)

- Sensitivity axis: **horizontal offset from the touch-down point**, not
  absolute screen position. Start anywhere; move right of the start for fine,
  left for coarse.
- Snap: **off by default**, toggled by a button above the waveform.
- Library access: **top-left button** (the left-edge swipe was not built).
- Background: a **radial glow tinted by the current pitch** (blurred album art
  was not built).

## Open questions

- Sensitivity constants: the exact $s_\text{coarse}$, $s_\text{fine}$, the
  half-travel $D$, and the dead-zone width need on-device tuning; the plan's
  values are a starting point.
- Whether the gear label should also nudge the pad visually (for example a
  subtle scale or tint change) as the user crosses into fine or coarse.
