# MusicFlow

Independent Android online music player. **Does not modify or depend on AudioBook.**

- Kotlin + Jetpack Compose
- Audius public music catalog: trending, search, audio streaming
- Media3 background player with media notification and lock-screen controls
- Favorites and queue, stored locally as metadata only
- No track downloading or offline audio storage

## Build APK without a PC

Open **Actions → Build MusicFlow APK**. After successful run, open its **Artifacts** section and download `MusicFlow-debug-apk`. Extract the ZIP and install `app-debug.apk` on Android (allow installation from the selected source).

Workflow builds an unsigned-for-release but debug-signed APK. For public distribution, a separate protected release signing workflow is required.

**Audius catalog availability may vary.** This project has no access to subscription music services.
