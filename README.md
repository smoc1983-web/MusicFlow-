# MusicFlow v0.7.5 Preview

Android, Kotlin, Jetpack Compose, Media3. Separate package: `com.musicflow.homeplaypreview`.

## One-tap playback on the homepage

- Selecting a Zaycev song stays on the home screen, resolves a matching playable stream in the background and starts it automatically in Media3.
- The Zaycev website pages expose song metadata, but not a confirmed direct MP3 link; MusicFlow resolves the selected artist/title against its playable music providers. It never sends the user to the search tab or a browser.
- A loading spinner on the selected song and an inline error on failure; songs with unverified artist/title matches are not played.
- Mini player stays available on the homepage, Search, Favorites and Logs, as well as in the full music player.
- The mini-player Close button and full-player Stop button call Media3 `stop()` and `clearMediaItems()` rather than merely pausing.

## TV

- Edge-to-edge immersive TV screen renders video under controls and hides system bars in fullscreen.
- Six picture modes: Original (original resolution where possible), Fit (aspect-correct), Fill (crop-to-cover), Stretch (distort to bounds), 16:9 and 4:3.
- First fullscreen expansion uses Fill to remove black borders; later size-mode selections are stored.
- Separate adaptive quality list only when multiple actual tracks are available. Seek buttons enabled only for seekable streams.

## Unchanged

- Muzofond and Mail.ru unified music search without Audius.
- Zaycev editorial recommendations, covers from public catalogs, favorites, Media3 queue, IPTV and diagnostic logs.
- v0.7.4 preserved in `stable/v0.7.4-preview`.
- APK is built with GitHub Actions; Android runtime must still be validated on a device.

## Build
GitHub Actions > Build MusicFlow APK > latest successful run > Artifacts > MusicFlow-preview-apk.
