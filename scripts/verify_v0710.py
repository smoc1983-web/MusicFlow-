"""Static safeguards for MusicFlow 0.7.10 source recovery.

These checks do NOT replace an Android build or on-device playback tests.
"""
from pathlib import Path

source = Path("app/src/main/java/com/musicflow/app")
main = (source / "MainActivity.kt").read_text(encoding="utf-8")
recovery = (source / "MusicPlaybackRecovery.kt").read_text(encoding="utf-8")
sources = (source / "MusicSources.kt").read_text(encoding="utf-8")
service = (source / "PlaybackService.kt").read_text(encoding="utf-8")
album = (source / "AlbumStreamResolver.kt").read_text(encoding="utf-8")

assert "val alternatives: List<MusicStream> = emptyList()" in main
assert 'put("alternatives", encodeStreams(t.alternatives))' in main
assert 'decodeStreams(obj.optString("alternatives"))' in main
assert '(original.streams() + track.streams()).distinctBy { it.uri }' in main
assert "putString(MUSIC_STREAMS, encodeStreams(t.streams()))" in main
assert 'putString(MUSIC_SOURCE, t.id.substringBefore(\':\'))' in main
assert 'player.addListener(MusicPlaybackRecovery(player, recoveryScope))' in service
assert 'private var pendingRecoveryToken: Int? = null' in recovery
assert "token == pendingRecoveryToken" in recovery
assert "putInt(recoveryTokenKey, transitionToken)" in recovery
assert "pendingRecoveryToken = transitionToken" in recovery
assert "mediaItem === replacement" not in recovery
assert "player.replaceMediaItem(index, updated)" in recovery
assert "player.prepare()" in recovery
assert 'findRecording(' in recovery
assert "attempted.size >= 6" in recovery
assert "withTimeoutOrNull(24_000L)" in sources
assert "recordingMatches(it, artist, title, durationSec)" in album
assert "playbackEnabled" in (source / "ZaycevSource.kt").read_text(encoding="utf-8")
print("MusicFlow v0.7.10 playback recovery static checks PASS")
