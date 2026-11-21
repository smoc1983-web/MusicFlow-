"""Static checks for v0.7.16 image quality, themed popup surfaces and playback recovery."""
from pathlib import Path

src = Path("app/src/main/java/com/musicflow/app")
main = (src / "MainActivity.kt").read_text(encoding="utf-8")
home = (src / "ZaycevHome.kt").read_text(encoding="utf-8")
cover = (src / "CoverLookup.kt").read_text(encoding="utf-8")
recovery = (src / "MusicPlaybackRecovery.kt").read_text(encoding="utf-8")
themes = (src / "MusicFlowAppearance.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")

assert "surfaceContainer = palette.surface" in main
assert "surfaceContainerHighest = palette.surface" in main
assert main.count("containerColor = palette.surface") >= 2
assert home.count("containerColor = palette.surface") >= 3
assert 'CACHE_VERSION = "hq4_"' in cover
assert "BitmapFactory.Options().apply { inJustDecodeBounds = true }" in cover
assert 'listOf("RU", "US")' in cover
assert '"cover_xl"' in cover
assert 'bounds.outWidth >= 500 && bounds.outHeight >= 500' in cover
assert ".firstOrNull { validHighResolution(it) }" in cover
assert "val wasPlaying = player.playWhenReady" in recovery
assert recovery.count("if (wasPlaying) player.play()") >= 3
assert "playlistGeneration == generation" in recovery
assert "override fun onPlaybackStateChanged(playbackState: Int)" in recovery
assert "override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int)" in recovery
assert "private fun armStartupWatchdog()" in recovery
assert "delay(12_000L)" in recovery
assert 'item.mediaId.startsWith("ruvideo:")' in recovery
assert "startupRetried = true" in recovery
assert "player.currentMediaItem !== failed" not in recovery
assert "MusicFlowStyle.AURORA" not in themes
assert "MusicFlowStyle.AB_ICY_NIGHT" in themes
assert 'versionName = "0.7.35"' in gradle
print("MusicFlow v0.7.16 visual/popup/playback static regressions PASS")
