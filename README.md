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


## v0.7.0 Preview — unified music search (Audius + Muzofond)

- One search field searches Audius and Muzofond concurrently, with a single combined track list and no visible source labels.
- The "Home" feed still uses Audius trending; non-empty searches query both sources.
- Muzofond is parsed directly on the Android device (no WebView or server-side proxy).
- Muzofond adapter uses the documented `/search/<query>` redirect to `/music/<query>` and the `li.item li.play[data-url]` track metadata.
- The same Media3 player handles both audio providers. Requests to Muzofond MP3 hosts include a scoped Referer header; Audius and IPTV requests are unchanged.
- Network failures in one source do not prevent searching/playing tracks from the other.
- No music downloads, scraping bypass, or WebView.
- **Limitation:** Muzofond may deny access to some IP regions/VPN addresses. It has not been validated from a Russian device; site layout may change.
- Separate application ID `com.musicflow.unifiedpreview` to protect the tested v0.6.0 build.

Parser based on the public source and unit fixture in:
https://github.com/Eremiteb/mytasks/tree/main/music_downloader


## v0.7.1 Preview — unified Mail.ru Music search

- Adds live search in the Mail.ru "My World" public music catalog alongside Audius and Muzofond.
- Search results are merged and de-duplicated by artist/title, without selecting or displaying a music provider.
- JSON API: `https://my.mail.ru/cgi-bin/my/ajax` with `func_name=music.search` and a public search query.
- Direct MP3 playback through the same Media3 controller; source-scoped Referer added for moosic.my.mail.ru.
- Verified on 2026-11-20 from GitHub Actions: mobile search HTTP 200; JSON search HTTP 200 with MusicData; sample MP3 range HTTP 206 audio/mpeg.
- Does not require an account, embed WebView or download music.
- Network or licensing restrictions may affect availability on other networks. Verified endpoint access does not guarantee every track plays.
- Protected previous version: `stable/v0.7.0-preview`; this version uses distinct application ID `com.musicflow.mailrupreview`.


## v0.7.2 Preview — local diagnostic log

- New tab Логи displays the most recent 150 lines, provides Refresh, Clear, and Send File buttons.
- Persistent file: filesDir/logs/MusicFlow_diagnostics.log, bounded to approx. 512KB with oldest entries trimmed.
- Records per-source music search counts/failures, IPTV playlist loads/failures, Media3 buffering and playback errors, session connection and app crashes.
- Neither song titles nor search query contents nor stream addresses are intentionally logged; recognizable URLs/tokens in errors are redacted.
- User-controlled file share via FileProvider and Android chooser; no automatic upload or storage permission.
- Installable alongside v0.7.1 with a separate package name (com.musicflow.logspreview).
