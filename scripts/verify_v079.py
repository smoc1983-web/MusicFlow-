from pathlib import Path
root = Path("app/src/main/java/com/musicflow/app")
main = (root/"MainActivity.kt").read_text(encoding="utf-8")
source = (root/"ZaycevSource.kt").read_text(encoding="utf-8")
playback = (root/"PlaybackService.kt").read_text(encoding="utf-8")
home = (root/"HomePlaybackResolver.kt").read_text(encoding="utf-8")
album = (root/"AlbumStreamResolver.kt").read_text(encoding="utf-8")
full = (root/"FullPlayerDialog.kt").read_text(encoding="utf-8")
assert 'zaycevTracks(requested)' in main
assert 'zaycevTracks(term)' in home
assert 'zaycevTracks(q)' in album
assert 'zaycev://track/' in source
assert 'pages/search/tracks' in source
assert 'track/filezmeta' in source and 'track/play/' in source
assert 'ZaycevStreamResolver.resolve(spec.uri)' in playback
assert 'ZaycevStreamResolver.resolve(Uri.parse(uri))' in album
assert 'id.startsWith("zaycev:")' in main
assert 'resultListState.scrollToItem(0)' in main
assert 'if (isActive) "♫" else "▶"' in full
assert 'resolvePlayableAlbumTrack(' in full
assert 'audiusTracks(' not in main
print("MusicFlow v0.7.9 Zaycev native playback and regression check PASS")
