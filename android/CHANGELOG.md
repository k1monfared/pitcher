# Changelog

## Unreleased

Usability fixes for the studio.

- Loop edges and bookmark pins can be grabbed at any zoom. The touch radius was
  compared in milliseconds instead of pixels, so at full zoom the target was
  under a pixel wide.
- Dragging a loop edge or a bookmark follows the finger for the whole drag and
  saves once on release. Before, the first move restarted the gesture.
- A loop edge can be dragged directly. Holding a loop or a pin without moving
  opens rename and delete, and tapping a loop selects it.
- Zoomed out, a plain drag on the waveform scrubs.
- Pitch momentum stops as soon as you touch the pad, and lifting a resting
  finger no longer flings. The +1 and +10 pills work with snap on.
- Tapping `original` returns to the original pitch, and the highlighted pitch
  always matches what is playing.
- Rendering no longer renames a pitch to its file name or clears its name.
  Loop-only renders are saved as files and no longer replace the pitch's full
  render. Making a loop no longer switches export to loops only.
- The render sheet shows the folder by name, can reset a song to the default
  folder, and the loop-only row toggles when tapped.
- A loop that ends at the end of the song keeps repeating, and loop seams are
  tighter.
- Playback pauses for calls and other audio apps, and stops when headphones are
  unplugged.
- Deleting a song asks first and no longer stops a different song that is
  playing.
- The bookmark button only adds. It no longer deletes a bookmark under the
  playhead.
- Back from the Library returns to the open song.
- Loop names accept spaces. The tuner shows detection progress and failures,
  and keeps the cents offset of a detected note (for example `A4+37`).

## 2.5.2

- Making a loop works at any zoom level. It used to need a 200 ms drag, so
  once the waveform was zoomed in a drag mapped to less time and nothing was
  created; it now depends on finger distance.
- Snap lights blue when on, with no tap flash.
- Waveform time stamps sit below the chart so they are never clipped, and
  bookmarks are fatter pins.
- Loop edges and bookmarks are easier to grab, and long-pressing the middle of
  a loop or a bookmark opens rename and delete.
- The export format is a bigger wheel centred on the file name, with a stepped
  drag.
- The onboarding speed step shows the speed HUD above the tour.

## 2.5.1

Fixes and polish from first use, plus an install fix.

- Installing a new version no longer requires deleting the old one. Releases
  are now signed with a stable key (see below); before, each build used a
  throwaway debug key, which Android treats as a different app.
- Fixed text being invisible in the studio and the library (black on black).
- The pitch pad no longer shows the fine/coarse label; snap is a colour chip.
- The bookmark button toggles a bookmark at the playhead.
- The loop lane is one gesture (drag to make a loop, hold then drag an edge),
  and the bars fill the lane.
- Long-press on the wave scrubs under your finger and commits on release.
- The render sheet puts the format picker inline as a draggable extension token,
  defaults the loop-only row off, and splits the action into rendering and a
  confirmed cancel. Renders run in the background and post a notification whose
  tap opens the folder.
- A pitch's name (shelf) and the file name (render sheet) are separate. Tapping
  a pitch opens the render sheet; long-press gives render, rename, share, and
  delete. The same cents can no longer be saved twice.
- The onboarding is shorter, and its speed step shows the speed HUD.
- The settings sheet no longer jitters when dragged.

## 2.5.0

A rebuilt pitch gesture, a real timeline, and a single render flow. The classic
interface is gone; the touch-first Studio is now the only one.

- Rebuilt pitch gesture: vertical, no absolute scale. The rate follows how fast
  you move (slow is fine, fast is coarse), the first few pixels are extra fine,
  and a flick keeps going and slows down until you catch it. Snap rounds on a
  light 20-cent grid with a stronger pull to semitones.
- The song chart holds multiple loops and bookmarks. Drag the wider lane to make
  a loop, long-press a loop edge or a bookmark pin to fine-tune it with a
  timestamp that follows your thumb. Tap the wave to seek, drag to pan when
  zoomed, long-press and drag to scrub, pinch to zoom.
- Loops and bookmarks have a drawer to rename, delete, enable, and select. Each
  loop can be toggled off, and a Spotify-style repeat button cycles whole song,
  all loops, or one loop.
- Renders concatenate the enabled loops with hard cuts into one file.
- One render sheet: the save address (tap to choose a folder for the song), an
  editable file name with a non-editable extension token and an always-open
  format strip, Save file and Share, and a cancel.
- A default save folder in Settings, with a per-song override.
- The tuner is a modal now (long-press the readout): keep a source and target
  note, detected at the playhead or a bookmark or typed, and apply the interval
  to the slider. It remembers the last notes per song.
- Settings is a floating gear; haptics can be turned off.
- The classic interface and the interface switch are removed.

## 2.0.1

A guided tour, cancellable renders, and a round of gesture and control fixes.

- A short guided tour appears the first time you open a song: it points at the
  pitch gesture, the readout, snap, the waveform, the loop lane, the play-speed
  chip, saving a pitch, and export. It can be skipped, and replayed any time
  from Settings.
- Renders, exports, and shares can now be cancelled from the Export sheet, the
  studio, or the classic Pitch Lab.
- Fixed keep-screen-on in the new interface; it was only applied in the classic
  one.
- The pitch pad has adjustable grain: hold left for coarse steps, right for
  1-cent fine. Snap to semitones works while dragging, and the readout shows the
  current gear. Plus and minus buttons step by 1, 10, or 100 cents.
- The waveform zooms with a pinch, pans on a drag, seeks on a tap (snapping to a
  nearby bookmark), and shows time stamps. Drag the thin lane above it to set an
  A/B loop.
- The play-speed control is a hold-and-slide preset picker; tap it to return to
  1x. A HUD shows the preset list while you drag.
- Add bookmarks from the studio, and long-press a saved pitch to rename, export,
  share, or delete it.
- A spinner shows while a pitch is being saved, and the Library shows a
  placeholder for missing titles.

## 2.0.0

A new touch-first interface, with the classic one kept as a fallback.

- New Studio home: a full-screen pitch gesture. Drag vertically to shift the
  pitch; slide left or right of where you touched to change how fast it moves,
  from coarse to fine, continuously. A HUD shows the value, a semitone scale,
  and the current gear, with haptics at each semitone.
- Giant live cents readout with the interval in words, and an ambient glow that
  shifts color with the pitch.
- SoundCloud-style waveform with draggable A/B loop handles.
- Speed chip: drag to change, tap to cycle presets, or hold to audition
  slower and spring back on release.
- Double-tap the pitch pad to reset to zero, or tap the cents readout to type
  an exact value.
- Per-song pitch carousel; the Library and the sheets move off the home screen.
- Render and export are separate: renders save to Music/pitcher and stay in the
  library; export opens the share sheet.
- Interface style is switchable in Settings (Modern or Classic). Both share the
  same engine, library, and playback.

## 1.0.2

- Rendered pitches are now saved into `Music/pitcher` and kept in the library,
  so a slow render is not lost if you forget to share it. The Pitches screen
  lists every pitch per song and marks which are saved.
- Render and export are separate actions: "Render & keep" saves the pitch to
  the library, and "Export / share" opens the share sheet (rendering first if
  needed). Each saved pitch can be shared or deleted from the Pitches screen.
- The pitch fader is centered so there is room to slide left for fine tuning
  and right to move fast.
- Exported file names are now `song - artist - pitch`, skipping the artist when
  there is none. An unnamed pitch uses its shift (for example `-600`), and a
  missing song title falls back to the original file name.

## 1.0.1

- Fix a crash when exporting. Rendering now uses much less memory: decode
  buffers are preallocated, the pitch shifter allocates one fewer buffer per
  channel, decoded channels are freed as they are shifted, and the app asks for
  a large heap. If stereo still does not fit, it retries as mono and reports a
  clear message instead of dying. Exports can no longer run two at a time.

## 1.0.0

First release.

- Transpose audio by any interval while keeping tempo, with live preview
  (Media3 Sonic).
- Touch scrub fader with fine/coarse zones, haptics, and double-tap reset.
- Monophonic YIN tuner (Kotlin port of the desktop algorithm) plus manual
  note/Hz entry and two-way target note/Hz sync.
- Kept pitch shelf, sorted by shift; playback position is preserved when
  switching pitches.
- A/B section loop and 0.5x to 2x playback speed.
- Zoomable waveform with pinch, pan, tap-to-seek, and 1s/5s steppers.
- Bookmarks stored per track.
- Import audio, or video with audio-only extraction. Files can also be shared
  into pitcher from other apps.
- Export and share as WAV, MP3, M4A, or Opus, optionally only the A/B loop,
  stereo where the device allows.
- Background playback with lock-screen controls; screen stays on while playing.
- Offline only: no network permission, no bundled downloader.
