# Changelog

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
