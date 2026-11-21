"""Static integration checks; an Android build and on-device test are still required."""
from pathlib import Path

root = Path("app/src/main/java/com/musicflow/app")
main = (root / "MainActivity.kt").read_text(encoding="utf-8")
home = (root / "ZaycevHome.kt").read_text(encoding="utf-8")
queue = (root / "HomePlaylistQueue.kt").read_text(encoding="utf-8")
sources = (root / "MusicSources.kt").read_text(encoding="utf-8")
recovery = (root / "MusicPlaybackRecovery.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")

assert "onPlay: (HomeSong, List<HomeSong>) -> Unit" in home
assert "onPlay(selected, collected)" in home
assert "onPlay(selected, popularQueue)" in home
assert "onPlay(selected, freshQueue)" in home
assert "val popularQueue = popular.take(20)" in home
assert "val freshQueue = fresh.take(15)" in home
assert "fun playHomeSong(song: HomeSong, section: List<HomeSong>)" in main
assert "buildHomePlaylistQueue(song, section, candidate)" in main
assert "queueAndPlay(queue.tracks, queue.selectedIndex, homePlaylist = true)" in main
assert "putBoolean(MUSIC_HOME_PLAYLIST, homePlaylist)" in main
assert "val selectedIndex: Int" in queue
assert "private val zaycevPage = Regex" in queue
assert 'stream = "zaycev://track/$numericId"' in queue
assert "selectedPlayable.alternatives +" in queue
assert "ordered.size" in queue
assert "internal const val MUSIC_HOME_PLAYLIST" in sources
assert "player.seekTo(nextIndex, 0L)" in recovery
assert "extras.getBoolean(MUSIC_HOME_PLAYLIST, false)" in recovery
assert 'versionName = "0.7.29"' in gradle
print("MusicFlow v0.7.11 collection queue static checks PASS")
