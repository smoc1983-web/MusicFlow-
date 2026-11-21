"""Static checks for MusicFlow v0.7.15 improvements, not device playback tests."""
from pathlib import Path
root = Path("app/src/main/java/com/musicflow/app")
theme=(root/"MusicFlowAppearance.kt").read_text(encoding="utf-8")
main=(root/"MainActivity.kt").read_text(encoding="utf-8")
home=(root/"ZaycevHome.kt").read_text(encoding="utf-8")
tv=(root/"RuVideoHubTv.kt").read_text(encoding="utf-8")
fullscreen=(root/"TvFullscreenActivity.kt").read_text(encoding="utf-8")
bridge=(root/"TvVideoSurfaceBridge.kt").read_text(encoding="utf-8")
mini=(root/"TvMiniPlayer.kt").read_text(encoding="utf-8")
cover=(root/"CoverLookup.kt").read_text(encoding="utf-8")
gradle=Path("app/build.gradle.kts").read_text(encoding="utf-8")
import re
themes=re.findall(r'^\s*(AB_[A-Z_]+)\("',theme,flags=re.M)
assert len(themes)==10, themes
assert "MusicFlowStyle.AB_ICY_NIGHT.name" in main
assert "?: AB_ICY_NIGHT" in theme
assert 'staticCompositionLocalOf { musicFlowPalette(MusicFlowStyle.AB_ICY_NIGHT) }' in theme
for forbidden in ('"Aurora Glass"', '"AMOLED Neon"', '"Midnight Violet"', '"Graphite Gold"', '"Soft Light"'):
    assert forbidden not in theme
assert "statusBarsPadding()" in main and "navigationBarsPadding()" in main
assert 'Text("Музыкальная лента"' in home and 'feedSection == "Популярное"' in home
assert "onPlay(selected, popularQueue)" in home and "onPlay(selected, freshQueue)" in home
assert "cub.red" not in tv.lower() and "CUB IPTV" not in tv
assert "importLocalIptvPlaylist" in tv and "newIptvSource(" in tv
assert "TvVideoSurfaceBridge.sharedController()" in fullscreen
assert "TvVideoSurfaceBridge.registerMainController" in main
assert "PlayerView.switchTargetView(" in bridge
assert "musicflow_tv_texture_player" in mini and "musicflow_tv_texture_player" in fullscreen
assert Path("app/src/main/res/layout/musicflow_tv_texture_player.xml").is_file()
assert "cover_xl" in cover and "cover_big" in cover
assert "CACHE_VERSION" in cover and "musicBrainz" in cover
assert "artistMatches" in cover and "verifiedArtist" in cover
assert "preferredProvided" in cover and "1200x1200bb" in cover
assert "versionName = \"0.7.20\"" in gradle
print("MusicFlow v0.7.15 artwork, AudioBook themes, navigation and TV checks PASS")
