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

## Bundled libraries

The web UI uses `soundtouchjs` (LGPL-2.1, compatible with this repo's GPL-3.0-or-later)
for live pitch and tempo preview in the browser. Files you keep are always rendered
server-side with the Rubber Band Library (GPL).

The Android app uses `jump3r` (LGPL, a Java port of LAME) for MP3 encoding, Android's
own `MediaCodec`/`MediaMuxer` for AAC and Opus encoding, and its own Kotlin WSOLA
implementation for pitch shifting. All are free and open source and compatible with
this repo's GPL-3.0-or-later license.

No proprietary or closed-source audio code is used anywhere.

## No warranty

Users are responsible for their own actions and any legal consequences. Only process audio
you have the right to process.
