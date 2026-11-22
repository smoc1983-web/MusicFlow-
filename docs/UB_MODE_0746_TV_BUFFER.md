# MusicFlow 0.7.46 — ub-mode IPTV buffer investigation

Baseline: signed MusicFlow v0.7.45.
Scope: HLS TV buffering and timeouts. Do not modify Pro, playlists, Equalizer or theme selection.

## First make it work
1. Inspected uploaded `look.m3u8` offline, without sending private stream links to third parties.
   - 722910 bytes, 3244 numbered M3U/HLS entries, 3244 distinct links, one HTTP host.
   - FHD appears in 139 names, HD in 1463, 4K/UHD in 54 (descriptors, not measured bitrates).
   - All channels use `.m3u8`; local file under existing 2MB import limit and 5000-item cap.
2. Attempted a bounded GET to an HLS entry. DNS resolution unavailable from the execution sandbox; this is not evidence that the server is down on the user's network.
3. Root cause visible in TV code: the old buffering watchdog restarted an HLS stream after only 14s and the frozen-position check after 12s. Default HTTP timeouts are shorter than a slow segment might require.
4. Wrote `TvPlaybackTuningTest` before editing the service, with regression checks for 25–75s buffer, 6s startup, 12s rebuffer, 64MiB target, 20s connect and 45s read timeouts, 65s buffering and 30s stalled-playhead threshold, and two-retry limit.

## Then make it right
5. Added `TvPlaybackTuning.kt` with conservative slow-network HLS settings.
6. Changed the existing Media3 Service to use `DefaultHttpDataSource.Factory` and `DefaultLoadControl`. Existing session and source resolution remain intact.
7. Increased `TvStreamRecovery` thresholds, retained 2 tries and avoided retry on permanent 403/404/401.
8. First CI run found that Media3 1.8.0 lacks `setBufferDurationsMsForStreaming`; changed to version-compatible `setBufferDurationsMs` and reran checks.
9. HLS playlist URLs and any account tokens are not committed or logged.

## Limitations and verification
- Profile is a **conservative selection**, not a measured optimal bitrate-dependent value. Real HLS manifest/segment timing and user's throughput cannot be measured from the M3U index alone.
- Larger buffer can smooth temporary drops but cannot sustain a stream encoded above the long-term network capacity. Use a lower-quality channel where available.
- The shared ExoPlayer's buffer duration settings also apply to its other streaming media; this is an acknowledged compatibility trade-off in Media3 1.8.0.
- CI: `.github/workflows/musicflow-v0746-tv-buffer-verify.yml`, Kotlin/JUnit and unsigned release compilation; record final run status after CI completes.
- CRAP/Dryer/Mutator do not implement Kotlin-specific gates here; no scores are claimed.
