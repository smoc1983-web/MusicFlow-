"""MusicFlow 0.7.37: home chart is one continuous player queue."""
from pathlib import Path
root=Path("app/src/main/java/com/musicflow/app")
main=(root/"MainActivity.kt").read_text(encoding="utf-8")
home=(root/"ZaycevHome.kt").read_text(encoding="utf-8")
service=(root/"PlaybackService.kt").read_text(encoding="utf-8")
queue=(root/"ChartPlaylistQueue.kt").read_text(encoding="utf-8")
recovery=(root/"MusicPlaybackRecovery.kt").read_text(encoding="utf-8")
player=(root/"FullPlayerDialog.kt").read_text(encoding="utf-8")
journal=(root/"PlaybackJournal.kt").read_text(encoding="utf-8")
gradle=Path("app/build.gradle.kts").read_text(encoding="utf-8")
assert "onPlayChart: (TopSong, List<TopSong>) -> Unit" in home
assert "onPlayChart(song, chartSongs.toList())" in home
assert "fun playChartSong(song: TopSong, chartSection: List<TopSong>)" in main
assert "ChartPlaylistQueue.build(song, chartSection, candidate)" in main
assert "chartPlaylist = true" in main
assert "ChartPlaylistQueue.parse" in service
assert "internal fun build(" in queue
assert "findRecording" in service
assert 'MUSIC_CHART_PLAYLIST' in player
assert "MUSIC_CHART_PLAYLIST" in recovery
assert "MUSIC_CHART_PLAYLIST" in journal
assert "versionCode = 737" in gradle and 'versionName = "0.7.37"' in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle
print("PASS: MusicFlow 0.7.37 top chart playlist and safe lazy resolution")
