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
