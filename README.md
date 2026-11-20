# MusicFlow — v0.7.7 Preview

## Album grouping
- Search and favorites automatically group songs by album **when** the provider supplies a reliable collectionTitle.
- Album groups show their artist, album title and track count, without splitting by provider.
- Tracks without album metadata remain in "Отдельные треки"; titles are not invented.
- When duplicate tracks arrive from Muzofond and Mail.ru, MusicFlow preserves the working stream and retains richer album/cover metadata.
- Tap an album track to queue that album's available tracks in their current listing order; previous/next works immediately in the player.

## Player
- Rebuilt the full music player as a fixed-layout window. Artwork shrinks to the remaining screen height.
- Transport controls, progress slider, playback/seek and shuffle/favorite/repeat stay anchored below content; they do not scroll off screen.
- Dedicated full-screen button makes the album artwork an immersive background and hides Android system bars.
- Previous/next stays accessible without returning to the results: uses the current Media3 queue when possible, or fetches a verified album list and resolves the adjacent song on demand.
- Album and queue views are reachable from icons in the player header; both keep the transport controls visible.
- If no adjacent playable track is available, an inline message explains why; no unrelated song is played.
- Close (X) still stops/clears the full playback queue; Back only closes the view.

## Other features preserved
- Music from Muzofond and Mail.ru; Zaycev editorial home; Audius removed.
- Cover lookup, fullscreen TV Activity, 6 picture-size modes, IPTV via URL and local M3U/M3U8 files, diagnostic exports.
- Previous v0.7.6 is preserved in `stable/v0.7.6-preview`.
- Separate installation package: `com.musicflow.albumplayerpreview`.

## Build
GitHub Actions → Build MusicFlow APK → Artifact. Android device runtime must be validated separately.
