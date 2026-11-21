"""MusicFlow v0.7.14 static playback-surface handoff regression checks.

These assertions check the implementation is wired end-to-end.
Only an Android build and playback on a device can verify video continuity.
"""
from pathlib import Path
src = Path("app/src/main/java/com/musicflow/app")
main = (src / "MainActivity.kt").read_text(encoding="utf-8")
mini = (src / "TvMiniPlayer.kt").read_text(encoding="utf-8")
fullscreen = (src / "TvFullscreenActivity.kt").read_text(encoding="utf-8")
bridge = (src / "TvVideoSurfaceBridge.kt").read_text(encoding="utf-8")
recovery = (src / "MusicPlaybackRecovery.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
assert 'Text(playbackStatus.orEmpty(), color = palette.accent' in main
assert "playbackSource" not in main
assert "MUSIC_SOURCE" in recovery
assert 'TvVideoSurfaceBridge.requestFullscreen()' in main
assert 'TvVideoSurfaceBridge.cancelFullscreenRequest()' in main
assert 'TvVideoSurfaceBridge.onMainResume()' in main
assert 'TvVideoSurfaceBridge.onMainPause()' in main
assert 'TvVideoSurfaceBridge.bindMini(view, player)' in mini
assert 'TvVideoSurfaceBridge.releaseMini(view)' in mini
assert 'TvVideoSurfaceBridge.bindFullscreen(view, player)' in fullscreen
assert 'TvVideoSurfaceBridge.releaseFullscreen(it)' in fullscreen
assert 'TvVideoSurfaceBridge.fullscreenDestroyed()' in fullscreen
assert "this.player = player" not in mini
assert "this.player = player" not in fullscreen
assert "view.player = player" not in mini
assert "view.player = player" not in fullscreen
assert "fullscreenRequested = true" in bridge
assert "mainIsResumed" in bridge
assert "mini?.get()?.player = null" in bridge
assert "if (!mainIsResumed || fullscreenRequested" in bridge
assert 'versionName = "0.7.19-preview"' in gradle
print("MusicFlow 0.7.14 TV surface handoff static checks PASS")
