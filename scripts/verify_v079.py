from pathlib import Path
root = Path("app/src/main/java/com/musicflow/app")
main = (root/"MainActivity.kt").read_text(encoding="utf-8")
source = (root/"ZaycevSource.kt").read_text(encoding="utf-8")
playback = (root/"PlaybackService.kt").read_text(encoding="utf-8")
home = (root/"HomePlaybackResolver.kt").read_text(encoding="utf-8")
album = (root/"AlbumStreamResolver.kt").read_text(encoding="utf-8")
full = (root/"FullPlayerDialog.kt").read_text(encoding="utf-8")
sources = (root/'MusicSources.kt').read_text(encoding='utf-8')
assert 'searchMusic(requested)' in main
assert 'resolvePlayableAlbumTrack(' in home
assert 'findRecording(artist, title, durationSec)' in album
assert '::zaycevTracks' in sources
assert 'zaycev://track/' in source
assert 'pages/search/tracks' in source
assert 'track/filezmeta' in source and 'track/play/' in source
resolver = (root/'MusicPlaybackSourceFactory.kt').read_text(encoding='utf-8')
assert 'musicPlaybackSourceFactory(this)' in playback
assert 'ZaycevStreamResolver.resolve(spec.uri)' in resolver
assert 'probeAlbumStream' not in album
assert 'id.startsWith("zaycev:")' in main
assert 'resultListState.scrollToItem(0)' in main
assert 'if (isActive) "♫" else "▶"' in full
assert 'resolvePlayableAlbumTrack(' in full
assert 'audiusTracks(' not in main
print("MusicFlow v0.7.9 Zaycev native playback and regression check PASS")
