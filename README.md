# MusicFlow v0.7.4 Preview

Android app, Kotlin + Jetpack Compose + Media3. Separate package: com.musicflow.artworktvpreview.

### New
- Album art from public iTunes metadata with MusicBrainz/Cover Art Archive fallback; exact artist/title matching, cached locally.
- Music player has -10 / +10-second seek buttons in addition to next/previous song, when media is seekable.
- TV player has an immersive fullscreen mode (dialog window expands, system bars hide, Activity requests landscape, restores on close).
- TV quality menu reads actual video variants from Media3 HLS/DASH tracks: Auto or an available supported height/bitrate. No artificial HD modes. Live TV seek buttons are disabled for unseekable sources.
- Homepage inspired by Zaycev's information architecture: popular, new music, genres and themed collections. Metadata is fetched from its public HTML and is updated on app startup. Selecting a song searches the existing playable catalogs.
- **Zaycev audio is not yet a direct stream source:** its HTML does not publish direct MP3 URLs. Zaycev songs are discovery entries and selecting them triggers native unified search in Muzofond and Mail.ru. Never misrepresent this as direct Zaycev playback.
- Audius stays removed. IPTV, custom playlists, logging and favorites remain intact.

### Build
GitHub Actions > Build MusicFlow APK > Artifacts > MusicFlow-preview-apk.
The previous build is preserved at stable/v0.7.3-preview.
