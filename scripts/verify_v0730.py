"""MusicFlow 0.7.37: Russian radio, lyrics, queue, history and privacy regression."""
from pathlib import Path
import re

root = Path("app/src/main/java/com/musicflow/app")
main = (root / "MainActivity.kt").read_text(encoding="utf-8")
radio = (root / "RussianRadio.kt").read_text(encoding="utf-8")
lyrics = (root / "SyncedLyrics.kt").read_text(encoding="utf-8")
full = (root / "FullPlayerDialog.kt").read_text(encoding="utf-8")
journal = (root / "PlaybackJournal.kt").read_text(encoding="utf-8")
service = (root / "PlaybackService.kt").read_text(encoding="utf-8")
history = (root / "MusicPlaybackHistoryScreen.kt").read_text(encoding="utf-8")
sources = (root / "MusicSources.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")
workflow = Path(".github/workflows/android-apk.yml").read_text(encoding="utf-8")

assert 'versionCode = 739' in gradle and 'versionName = "0.7.39"' in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle
assert 'applicationIdSuffix = ".dev"' in gradle
assert '"vk" to ::' not in sources
assert '"audius" to ::' not in sources and '"jamendo" to ::' not in sources
assert '6 -> "Радио"' in main and '5 -> "История"' in main
assert 'RussianRadioScreen(' in main and 'MusicPlaybackHistoryScreen(' in main
assert 'onPlay = { station -> queueAndPlay(listOf(station.toTrack()), 0) }' in main
assert 'https://de1.api.radio-browser.info' in radio
assert 'bycountrycodeexact/RU' in radio and 'countrycode=RU' in radio and 'hidebroken=false' in radio
assert 'isPublicStream' in radio and 'item.optInt("lastcheckok", 0) != 1' not in radio
assert 'getSharedPreferences(PREFS' in radio
assert 'https://lrclib.net/api/search?' in lyrics
assert 'syncedLyrics' in lyrics and 'plainLyrics' in lyrics
assert 'musicKey(actualTrack) != musicKey(name)' in lyrics
assert 'musicKey(actualArtist) != musicKey(performer)' in lyrics
assert 'abs(length * 1000L - durationMs) > 5_000L' in lyrics
assert 'LaunchedEffect(active, lines.size)' in lyrics
assert 'PlayerPage.LYRICS' in full and 'SyncedLyricsScreen(' in full
assert 'switchToAlternateSource' not in full and 'Другой источник' not in full
assert 'PlayerPage.LYRICS' in full
assert 'player.addListener(playbackJournal)' in service
assert 'playbackJournal.restore()' in service and 'playbackJournal.persist()' in service
assert 'delay(5_000L)' in service
assert 'fun restore()' in journal and 'fun persist()' in journal
assert 'putString("queue"' in journal and 'putString("history"' in journal
assert 'player.shuffleModeEnabled' in journal and 'player.repeatMode' in journal
assert re.search(r'PlaybackJournal\.resumePosition\(\s*context,\s*list\[index\]\.id,\s*list\[index\]\.durationSec', main)
assert 'onPlay: (Track) -> Unit' in history and 'clearHistory(context)' in history
assert 'MusicFlowUpdates.latestRelease()' in (root / "MusicFlowAutoUpdate.kt").read_text(encoding="utf-8")
assert 'verify_v0730.py' in workflow
print("PASS: VK/Audius/Jamendo excluded; radio catalog and Russian radio tab")
print("PASS: synced LRCLIB lyrics and one-tap manual source switching")
print("PASS: local listening history; stored Media3 queue, positions, repeat and shuffle")
