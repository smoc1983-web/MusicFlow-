"""Regression from v0.7.35: chart playback remains matched and search resets."""
from pathlib import Path
root=Path("app/src/main/java/com/musicflow/app")
home=(root/"ZaycevHome.kt").read_text(encoding="utf-8")
main=(root/"MainActivity.kt").read_text(encoding="utf-8")
player=(root/"FullPlayerDialog.kt").read_text(encoding="utf-8")
top=(root/"TopSongs.kt").read_text(encoding="utf-8")
gradle=Path("app/build.gradle.kts").read_text(encoding="utf-8")
assert 'onPlayChart = { song, section -> playChartSong(song, section) }' in main
assert "TopSongsDirectory.fetch(chartPeriod, chartRegion)" in home
assert "findRecording(song.artist, song.title)" in main
assert "LaunchedEffect(tab, appliedSearch, searchMode, sortMode, tracks)" in main
assert main.count("resultListState.requestScrollToItem(0)") >= 3
assert "Другой источник" not in player
assert "switchToAlternateSource" not in player
assert 'https://api.listenbrainz.org/1/stats/sitewide/recordings' in top
for period in ("this_week", "this_month", "this_year"):
    assert period in top
assert "listen_count" in top and "track_name" in top and "artist_name" in top
assert "versionCode = 737" in gradle and 'versionName = "0.7.37"' in gradle
print("PASS: chart-backed player, scrolling, clean UI (0.7.37)")
