# MusicFlow v0.7.6 Preview

Kotlin, Jetpack Compose, Media3. Separate installation package `com.musicflow.fulltvpreview`.

## Fixed TV fullscreen

- Replaced the Compose Dialog with a standalone immersive landscape Android Activity. Video fills the real Activity window, not a dialog rectangle that leaves underlying MusicFlow visible at the left.
- System bars are hidden, landscape and display cutout space are handled by Android.
- Top overlay buttons **Размер**, **Качество** and **Каналы** remain accessible in horizontal mode.
- Picture modes: Original, Fit, Fill (default), Stretch, 16:9, 4:3. Size preference is saved.
- Real Media3 available HLS/DASH quality choices and seek for seekable streams. Channel switch and playlist refresh remain accessible.

## Local IPTV playlists

- **+ Файл** accepts user-selected M3U/M3U8 files via Android OpenDocument, no storage permissions.
- Files are copied to private app storage, validated as channel playlists, limited to 2 MB, and survive app restarts.
- URLs must be absolute HTTP(S). Local relative paths or raw HLS segment playlists cannot act as multi-channel IPTV files.
- Imported playlists can be selected, refreshed and deleted; deletion removes the private copy.
- Existing URL playlist and single-stream channel support preserved.

## Album track lists

- In the full music player, **Альбом · Все композиции** opens MusicBrainz release track listing.
- When album metadata exists, exact album name is used; otherwise verified releases containing the song are searched.
- Tap a listed song to resolve playable audio via Muzofond/Mail.ru and start directly in the native Media3 player.
- The release list is metadata, not a streaming source: not all album tracks are necessarily available for playback. Unknown album data is reported, never fabricated.

## Unchanged

- Album artwork lookup, Zaycev home catalog with one-tap matching-track playback, global source-neutral search, Stop music, favorites, diagnostic log.
- Audius remains removed.
- Prior v0.7.5 retained in `stable/v0.7.5-preview`.
- Compile tested via GitHub Actions; on-device UI still requires validation.
