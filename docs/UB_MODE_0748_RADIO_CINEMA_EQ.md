# MusicFlow 0.7.48 TEST — ub-mode engineering log

Baseline: isolated MusicFlow Cinema Scroll TEST v0.7.47, branch `feature/musicflow-v0747-cinema-scroll-test`; official app stays v0.7.46.
Target: a separate debug package `com.musicflow.preview.radiocinemaeqtest48`. No production signing or release publication.

## First make it work

1. Reviewed uploaded screenshots: radio rows show generic radio icon when provider artwork is absent, a station player has an unhelpful empty/generic cover, and cinema player labels a single audio rendition without opening a selector.
2. Traced radio image sources: reserve playlist's `tvg-logo`, optional Radio Browser metadata cache. Existing `rememberAsyncImagePainter` did not provide a robust visible error fallback. Wrote `RadioStationArtworkFallbackTest` before implementing stable station-name lettermarks.
3. Traced audio output: music, radio and TV use `PlaybackService` with `PcmEqAudioProcessor`, but cinema creates an independent `ExoPlayer` without the processor. Added EQ button to every player and the independent PCM audio sink plus preference listener for cinema; no onToggle pause, seek or prepare calls.
4. Traced the cinema audio picker: the TextButton was disabled and popup forced closed when `audioOptions.size <= 1`. Wrote `CinemaAudioPickerPolicyTest` before changing this behavior. The new popup opens for zero/one/many tracks and reports the actual number; for two or more published playable renditions the existing `TrackSelectionOverride` selects the chosen track. It never invents a dubbing that the stream does not provide.

## Then make it right

5. Added `RadioStationArtwork`: uses actual logos when available and readable, stable monogram art on missing/broken/loading imagery. Also used for radio mini and full player.
6. Added a reusable `PlayerEqualizerButton` composable, reachable from full/mini music/radio, TV compact and full-screen, classic TV dialog, and cinema inline/fullscreen. Removed EQ from top-level settings menu. Existing saved `AudioEqSettings` are shared.
7. Registered/unregistered changes to EQ preferences inside cinema's independent ExoPlayer lifecycle and configured Media3 with PCM16 processor. Existing `PlaybackService` remains unchanged.
8. Kept the existing five Liquid Glass themes, cinema 0.7.47 scroll fix, reserve radio stream source and Pro branch unchanged.
9. User requested **five radio-player design concepts only**. Do not change player layout without selection; concepts were presented separately.

## Verification

- Dedicated GitHub Actions `.github/workflows/musicflow-v0748-radio-cinema-eq-test.yml`: static integration guards; all `:app:testDebugUnitTest` suites; `:app:assembleDebug`; `aapt` exact isolated test package verification; `apksigner verify`; artifact SHA-256.
- New regressions: `RadioStationArtworkFallbackTest`, `CinemaAudioPickerPolicyTest`; pre-existing cinema scroll and PCM processor regression suites.
- **Limitations:** real RUTUBE HLS/dubbing availability and Bluetooth DSP output cannot be verified by JVM tests; test on Samsung S24. Stream with one audio track cannot magically expose an alternative dubbing.
- CRAP, Dryer and Mutator repository gates do not support Kotlin here; no invented metrics.
- Publication gate: no official APK or GitHub Release, user requested test only.
