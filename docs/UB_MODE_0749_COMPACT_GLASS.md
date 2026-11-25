# MusicFlow 0.7.49 — Compact Glass official update (ub-mode)

## Baseline and user selection
- Official release at start: `v0.7.46` (package `com.musicflow.preview`).
- Most recent completed debug test source: `feature/musicflow-v0748-radio-eq-cinema-audio-test`, successful CI 45111271474 (33 Kotlin/JUnit suites).
- User selected radio design **#2 Compact Glass**, and explicitly authorized signing + official in-app release. Incorporate all changes from the latest TEST 0.7.48.
- Original release signing cert SHA-256: `c461ee635d5b295ef6c765e1ae533d0079dc6e53cb2632a11a1b445773752410`. Keep keystore/password outside repository.

## First Make It Work
1. Inspected `FullPlayerDialog.kt` and the latest test branch before edits. The prior radio branch occupied a full-screen media player with a stretched square artwork region and bottom bar.
2. Created `RadioCompactGlassPolicyTest.kt` before UI changes. The expected live-radio actions are Play/Pause, Favorite, Equalizer, Stop/Close and Collapse. Seeking, Next/Previous, Repeat and Shuffle are deliberately unsupported.
3. Added `RadioCompactGlassPolicy.kt` for explicit compact/live transport state and user-visible status.
4. Added a standalone `CompactGlassRadioDialog.kt` with a horizontally arranged station logo, title, state and directly accessible radio controls inside one translucent glass card.
5. Routed only `radio:` media IDs to the compact dialog. Music player behavior and external stream logic remain intact.

## Then Make It Right
6. Used the app's palette-aware `musicFlowGlass`, `RadioStationArtwork` with fallback monograms and the existing shared `PlayerEqualizerButton`. All five Liquid Glass palettes remain supported.
7. Restored official launcher label MusicFlow, production package name and versionCode 749 / versionName 0.7.49; debug remains isolated under `.dev`.
8. Inherited from 0.7.48: radio logo fallback, in-player EQ for music/radio/TV/cinema, stream-backed audio track picker with honest single-track status, and the 0.7.47 cinema search/catalog scroll correction.
9. Inherited from signed official 0.7.46: TV buffering settings, update prompts and five theme collection. No Pro branch changes.
10. No signing secrets, raw keystores or private playlists are committed.

## Verification gate
- Dedicated workflow: `.github/workflows/musicflow-v0749-verify.yml` runs static integration guards, all Kotlin/JUnit regression suites, `assembleRelease`, `zipalign`, `aapt` package/version checks, and stages original Android `apksigner` for offline signing.
- A preliminary CI guard used a too-short excerpt when checking the call/return; corrected CI assertion without changing functional code.
- Official publication workflow: `.github/workflows/publish-v0749-original-signed.yml`. It verifies exact CI source SHA, unsigned/signed hashes, APK Signature v2, original cert fingerprint, package and version *before* GitHub Releases publication.
- Hardware playback, stream availability and in-app Android install flow require final device confirmation; JVM tests do not substitute for manual UI checks.
- CRAP / Dryer / Mutator tooling is not available for Kotlin in this repo; no unsupported scores claimed.

## Final checks — verified
- CI `45116005390` succeeded on app commit `d079cf05f5ba8afc2fd57db89fb162d4fbf7fae3`: 34 Kotlin/JUnit suites, `:app:assembleRelease`, zipalign and `aapt` identity checks.
- Original-key signing completed locally with no keystore or passwords in the repository. Android APK signatures v2 and v3 verify, signer digest `c461ee635d5b295ef6c765e1ae533d0079dc6e53cb2632a11a1b445773752410`.
- SHA-256 of official APK: `b01c993c2971dd0cedabcbf574ee9142314a65d1d1feab1500372bc42730242e`. Public signed-byte delta independently reconstructs identical APK bytes from the CI artifact.
- Official publication GitHub Actions `45116607903` succeeded. Latest GitHub release: `https://github.com/smoc1983-web/MusicFlow-/releases/tag/v0.7.49`, includes APK and SHA256SUMS, source `com.musicflow.preview` versionCode 749, not a beta build.
- In-app update feed `releases/latest` confirmed `v0.7.49`. Manual verification on Samsung S24 still recommended for the new radio dialog, Bluetooth EQ and stream-backed dubbing.
- The preliminary CI run failed only because a static assertion searched a 250-character excerpt instead of the full invocation; that check was corrected before the successful build.
