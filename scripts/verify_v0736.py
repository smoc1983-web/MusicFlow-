"""0.7.37: charts move next to Popular; Russia and World with real period data."""
from pathlib import Path
p=Path("app/src/main/java/com/musicflow/app")
home=(p/"ZaycevHome.kt").read_text()
main=(p/"MainActivity.kt").read_text()
charts=(p/"TopSongs.kt").read_text()
gradle=Path("app/build.gradle.kts").read_text()
assert 'listOf("Популярное", "ТОП песен", "Новинки")' in home
assert 'Text("Музыкальная лента"' not in home
assert 'TopSongsDirectory.fetch(chartPeriod, chartRegion)' in home
assert 'itemsIndexed(chartSongs,' in home
assert 'TopSongsRegion.RUSSIA' in home and 'TopSongsRegion.entries' in home
assert 'TopSongsRegion.WORLD' in charts
assert 'onPlayChart = { song, section -> playChartSong(song, section) }' in main
assert '7 -> "ТОП песен"' not in main
assert '7 to "ТОП песен"' not in main
assert 'else if (tab == 7)' not in main
assert 'enum class TopSongsRegion' in charts
for key in ('7d/', 'YYYY-MM', 'Europe/Moscow', 'ol.rows > li.row', 'a.title', 'span.artists', 'span.num'):
    assert key in charts, key
assert 'https://api.listenbrainz.org/1/stats/sitewide/recordings' in charts
assert "versionCode = 739" in gradle and 'versionName = "0.7.39"' in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle
print("PASS: MusicFlow 0.7.37 home charts and signed update compatibility")
