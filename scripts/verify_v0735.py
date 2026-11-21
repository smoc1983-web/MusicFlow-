"""0.7.35 contract: global top tracks tab, weekly/monthly/yearly stats, no bottom source button."""
from pathlib import Path

root = Path("app/src/main/java/com/musicflow/app")
main = (root / "MainActivity.kt").read_text(encoding="utf-8")
player = (root / "FullPlayerDialog.kt").read_text(encoding="utf-8")
top = (root / "TopSongs.kt").read_text(encoding="utf-8")
gradle = Path("app/build.gradle.kts").read_text(encoding="utf-8")

assert '7 -> "ТОП песен"' in main
assert '7 to "ТОП песен"' in main
assert "TopSongsScreen(" in main
assert "onPlay = { track -> queueAndPlay(listOf(track), 0) }" in main
assert "Другой источник" not in player
assert "switchToAlternateSource" not in player
assert 'https://api.listenbrainz.org/1/stats/sitewide/recordings' in top
for period in ("this_week", "this_month", "this_year"):
    assert period in top
assert "findRecording(song.artist, song.title)" in top
assert "listen_count" in top and "track_name" in top and "artist_name" in top
assert "versionCode = 735" in gradle and 'versionName = "0.7.35"' in gradle
assert 'applicationId = "com.musicflow.preview"' in gradle
print("PASS: top tracks chart and clean player (0.7.35)")
