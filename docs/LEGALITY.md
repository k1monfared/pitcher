# LEGALITY

Pitcher is a general-purpose audio tool. Whether a given use is permitted depends on the
audio, the source, your jurisdiction, and the platform's terms.

## Local files

Processing audio files you own or have the right to use is the clean path. Pitcher does
not circumvent DRM.

## YouTube / SoundCloud (yt-dlp)

Pitcher's importer shells out to `yt-dlp`. Downloading from YouTube and SoundCloud is
against those platforms' terms of service. Do not use the importer for content you are
not permitted to download or process.

## Spotify

Spotify exposes no public API for audio streams. Any tool that "downloads from Spotify"
(spotDL, spotifydown, and similar) resolves track metadata from Spotify and then pulls the
actual audio from a different provider, usually YouTube. This is against Spotify's terms
and likely copyright law. Pitcher follows the same model if a Spotify URL is given: it
resolves metadata only. The audio still has to come from a source you are allowed to use.

## License and libraries

pitcher is licensed under the GPL-3.0 (see `LICENSE`).

Desktop and web: the Rubber Band Library (GPL-2.0-or-later) shifts pitch, aubio
(GPL-3.0) is the optional tuner, ffmpeg (LGPL or GPL, depending on the build)
decodes and encodes, and yt-dlp (Unlicense) imports from URLs. The web UI previews
in the browser with `soundtouchjs` (LGPL-2.1). All are free and open source and
compatible with the GPL-3.0.

Android: every library the app ships is free and open source. Jetpack Compose,
AndroidX, and Media3 (ExoPlayer and its Sonic engine, used for both playback and
saved files) are Apache-2.0. MP3 encoding is `jump3r` (LGPL-2.1, a Java port of
LAME).

Android also relies on the codecs built into the phone, for decoding imported songs,
for playback, and for encoding M4A and Opus. These are not shipped with pitcher.
Phones usually carry two kinds: Android's own open-source codecs (the AOSP
`c2.android` family) and the phone maker's closed-source ones. pitcher always picks
Android's own codecs and uses a maker's codec only if the phone has nothing else for
that format. So on a typical phone the whole audio path is open source.

## Formats

- WAV is uncompressed and unencumbered.
- MP3's patents have expired, and pitcher encodes it in-app with LAME (jump3r).
- Opus is an open, royalty-free format. Android's Opus encoder is libopus (BSD).
- M4A (AAC) is a standardized but patent-licensed format. Android's own AAC codec
  is Fraunhofer's FDK AAC. Its license is free in the FSF's definition but is not
  GPL-compatible, and Debian classes it as non-free. pitcher does not ship it, it
  only calls the phone's codec. If you want a fully free chain, save as WAV, MP3,
  or Opus.

## No warranty

Users are responsible for their own actions and any legal consequences. Only process audio
you have the right to process.
