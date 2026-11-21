# MusicFlow Pro — integration stage 1 (debug only)

Source branch: `feature/musicflow-pro-integration-stage1`, based on official MusicFlow v0.7.38.

### Purpose
The separate APK prototype passed user-side testing. This branch connects the *same signed config patch experiment* to MusicFlow's existing Compose UI **only for debuggable builds**; no production Pro feature is enabled. The real package ID in release remains `com.musicflow.preview`. Debug builds automatically use the existing `.dev` suffix and may install beside the official app.

### User test (debug APK)
1. Open MusicFlow > top-right ellipsis > **MusicFlow Pro · тест**.
2. Confirm demo download is disabled.
3. Simulate a payment (does **not** charge real money).
4. Retrieve the signed activation manifest from `feature/musicflow-pro-demo-patch`.
5. Download the original, generated test WAV from the same separate branch to Android MediaStore.
6. Reset demo activation and verify the download button is locked again.

### Technical safety boundaries
- RSA signature verification on the exact patch payload.
- A license check is **not** implemented; the demo payment flag is local and easily forged.
- Debug-only control, no release-channel integration and no UI downloads of catalog tracks.
- No payment keys, signing keys, raw keystores, paid APIs or copyrighted music delivered.
- The code must not be represented as production subscription verification.
- Private license server + payment provider verification + track-level download permissions required before actual paid downloads.

### Testing
`gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug`

The GitHub Actions stage-1 workflow runs unit tests, validates the debug applicationId and stores a separately installable APK.
