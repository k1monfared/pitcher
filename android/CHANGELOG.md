# Changelog

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
