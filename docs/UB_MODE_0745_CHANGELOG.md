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
- Latest result to be updated after the full CI compilation.

**The ten existing themes and Pro branches remain untouched.**
