# MusicFlow 0.7.45 — ub-mode engineering log

**Baseline:** Official GitHub release v0.7.44.
**Target:** Production package `com.musicflow.preview`, versionCode 745. No new public release until authorization.
**Persistent instructions:** root `AGENTS.md`, `.agents/skills/ub-mode/SKILL.md`.

## First Make It Work
1. Inspected current `MusicFlowAutoUpdate`, background download and installed-version checks. Problem: modal closed only following a suspending DownloadManager operation; asynchronous errors left it on-screen. Wrote `UpdateDialogGateTest` before the reducer and UI patch.
2. Inspected native session effects and Bluetooth routing. Problem: attaching Android platform Equalizer/LoudnessEnhancer to the active AudioTrack can interrupt audio on output changes; hardware offload disabling did not resolve user's symptom. Wrote PCM DSP tests before implementing `PcmEqEngine`, `PcmEqAudioProcessor` and the in-stream sink. Removed the unused native effect implementation.
3. Inspected existing radio M3U `tvg-logo` parsing and Radio Browser documentation. Wrote artwork identity/security tests before adding metadata-only cached lookup. Kept the backup M3U as the only radio station source.
4. Added an explicit stop-and-close control to both mini and fullscreen radio players. Kept all existing themes unchanged pending a user-selected Liquid Glass design.

## Then Make It Right
5. Implemented idempotent per-version update prompt dismissal in the click handler, ahead of any network or installer action; persisted the ignored release and retained manual update access.
6. Used an always-connected PCM16 audio processor, volatile EQ settings and no native AudioEffect or playback commands during slider changes.
7. Used bounded concurrent metadata fetch, exact station title matching, HTTPS validation, cached logos and no new station streams.
8. First compile caught a Kotlin nullable smart-cast in the release parser; corrected it and reran CI.

## Practice and verification
- Regressions: `UpdateDialogGateTest`, `PcmEqEngineTest`, `PcmEqAudioProcessorTest`, `RadioArtworkCatalogTest`.
- CI: `.github/workflows/musicflow-v0745-verify.yml`.
- Hardware Bluetooth route and interactive Android installer behavior cannot be fully proven by JVM tests; device testing remains required.
- CRAP / Dryer / Mutator repositories list Clojure, Java, Go, TypeScript, Rust and Python, but not Kotlin. These specialized quality gates are not run; no unsupported metric claims.
- **Verified:** GitHub Actions run 45037932386, Kotlin/JUnit `testDebugUnitTest` and `assembleRelease` both successful. 28 test result suites emitted, including all four targeted regression classes. Unsigned production APK `com.musicflow.preview` versionCode 745 / 0.7.45 verified with `aapt`.
- First CI run 45037657370 failed because a nullable latest-release object lost smart-cast information in the update dialog; fixed the real compilation issue and reran tests successfully.
- No signed APK or GitHub Release published; release signature verification and publication are a separate gate.

## Liquid Glass redesign — user selected theme 1
1. Before changing code, inspected `MusicFlowAppearance.kt`, `MusicFlowGlass.kt`, `MusicFlowMenuStyle.kt`, the `MainActivity` picker and the persisted `ui_theme` key. The legacy enum exposed ten `AB_*` values; default was `AB_ICY_NIGHT`.
2. Added `MusicFlowLiquidGlassThemesTest.kt` before implementation to define exactly five styles, Obsidian defaults, preference round-trips and safe migration of all ten old keys. A separate red CI test run was not performed.
3. Replaced legacy palette options with Obsidian Glass, Arctic Crystal, Pearl Vision, Emerald Flow and Solar Glass; Obsidian is the default for fresh and migrated installs. Only the five new styles appear in the picker.
4. Added lightweight layered glass gradients, translucent backgrounds, directional border highlights and shadow. Reused native Compose shapes without expensive content blur or bitmap assets.
5. Preserved all other user data, MusicFlow package identity, playback functions and Pro branch. Stored `ui_theme` values are migrated to the selected Obsidian default, then future choices persist by new name.
6. Removed a redundant duplicate backdrop gradient rather than drawing two overlapping copies.
7. **Verified:** GitHub Actions run `45038975675` succeeded at source commit `95f18ba88635ae335120f7b1c937c6f2553fe335`. Kotlin/JUnit `testDebugUnitTest` and production `assembleRelease` both passed: 29 test suites, including `MusicFlowLiquidGlassThemesTest` and the existing PCM EQ/update/radio regressions. Android `aapt` confirmed package `com.musicflow.preview`, versionCode `745`, versionName `0.7.45`. Unsigned validation APK only, no release publication.
8. CRAP / Dryer / Mutator do not support Kotlin; not executed and no metric values claimed.

**The ten old theme choices have been replaced in the working branch. No new signed official release has been published.**
