# MusicFlow · Audius Preview

An independent Android online music player. **AudioBook is not affected.**

## Version 0.4.2 Preview (Audius only)
- **Audius is the sole online catalog**: trending tracks, artist/title search, sorting and favorites.
- Media3 online playback in background, media notification and lock-screen controls.
- Full-screen now-playing interface with artwork, player queue, shuffle, repeat, and favorite toggle.
- Smooth seeking: the slider follows the finger; a single seek is submitted on drag completion.
- Removed the Internet Archive / Live Music Archive source, its source filter, and source implementation.
- No track downloading or offline music storage.

This preview installs separately as `com.musicflow.audiuspreview`.
The last tested multi-source build remains preserved in branch `stable/v0.4.1-preview`.

## Build
GitHub Actions → Build MusicFlow APK → latest successful run → Artifacts → MusicFlow-preview-apk.
Debug APK signing is not guaranteed to be persistent across runner instances. Use a protected release signing key for distributed app updates.

Audius availability depends on connectivity and may require VPN in some networks.


## v0.5.0 Preview: RuVideoHub IPTV

- Separate **ТВ** tab with search, groups and an optional music-channel filter.
- Loads the **external** community-maintained M3U8 playlist at runtime from:
  https://github.com/ByteBudda/RuVideoHub/blob/main/playlist_iptv.m3u8
- Uses AndroidX Media3 HLS and PlayerView for live television/radio.
- Stream playback is available only when the provided stream is still valid and accessible.
- Shows expired stream-link hints, error messages and a refresh control.
- Does **not** bundle, download or redistribute the playlist or RuVideoHub Android code.
- RuVideoHub's README advertises many features, but no Android source code was available in the inspected repository; only the playlist was integrated.
- Audio search and playback remain Audius-only; Internet Archive is not reintroduced.
- The preview has an isolated package name, com.musicflow.tvpreview.

Original playlist and project © RuVideoHub / ByteBudda. See their License.md.
The repository contains GPLv3 language as well as additional non-commercial restrictions, so do not redistribute third-party material commercially without separate rights clarification.


## v0.5.1 Preview — TV player improvements

- Full-screen TV player with optional landscape rotation.
- Previous/next channel controls and in-player searchable channel list.
- Keep screen awake for TV viewing; restore orientation on close.
- Fit/zoom video display, pause/resume and live buffering/error indicators.
- One-tap retry or reload the RuVideoHub M3U8 playlist to refresh signed channel links.
- TV playback remains online-only, with Audius music untouched.
- Isolated application ID: `com.musicflow.tvplayerpreview`; v0.5.0 remains under `stable/v0.5.0-preview`.
