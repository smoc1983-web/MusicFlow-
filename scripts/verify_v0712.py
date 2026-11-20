"""Static UI integration checks. Android compilation and device tests are separate."""
from pathlib import Path
base = Path("app/src/main/java/com/musicflow/app")
main = (base / "MainActivity.kt").read_text(encoding="utf-8")
home = (base / "ZaycevHome.kt").read_text(encoding="utf-8")
cover = (base / "CoverLookup.kt").read_text(encoding="utf-8")
player = (base / "FullPlayerDialog.kt").read_text(encoding="utf-8")
tv = (base / "RuVideoHubTv.kt").read_text(encoding="utf-8")
full = (base / "TvFullscreenActivity.kt").read_text(encoding="utf-8")
mini = (base / "TvMiniPlayer.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
assert 'listOf(0 to "Музыка", 2 to "Избранное", 3 to "ТВ")' in main
assert 'contentDescription = "Поиск"' in main
assert 'Text("Что послушаем?' not in home
assert 'Text("Подборки"' in home and 'Text("Жанры")' in home
assert home.count("DropdownMenu(") >= 2
assert "OutlinedTextField(" in home
assert "onPlay(selected, collected)" in home
assert "onPlay(selected, popularQueue)" in home
assert "onPlay(selected, freshQueue)" in home
assert "TvMiniPlayer(" in main
assert "rotatedToLandscape" in main
assert 'LocalConfiguration.current.orientation' in main
assert "selected = channel" in tv
assert "TvPlaybackHandoff.set(channels, channel, activeSourceId)" in tv
assert 'context.startActivity(Intent(context, TvFullscreenActivity::class.java))' not in tv
assert "TvPlaybackHandoff.clear()" not in full
assert "PlayerView(ctx)" in mini and "onRelease = { view -> view.player = null }" in mini
assert "onExpand: () -> Unit" in mini
assert "1200x1200bb" in cover and "front-1200" in cover
assert "needsUpgrade(provided)" in cover
assert "Brush.verticalGradient" in player
assert 'versionName = "0.7.12-preview"' in gradle
print("MusicFlow v0.7.12 UI and TV mini-player integration PASS")
