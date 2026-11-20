from pathlib import Path
root=Path("app/src/main/java/com/musicflow/app")
main=(root/"MainActivity.kt").read_text(encoding="utf-8")
home=(root/"ZaycevHome.kt").read_text(encoding="utf-8")
tv=(root/"TvPlayerDialog.kt").read_text(encoding="utf-8")
full=(root/"FullPlayerDialog.kt").read_text(encoding="utf-8")
resolver=(root/"HomePlaybackResolver.kt").read_text(encoding="utf-8")
assert 'onPlay = { song, section -> playHomeSong(song, section) }' in main
assert '.clickable(enabled = pendingSongId != song.id) { onPlay(song) }' in home
assert 'controller.stop()' in main and 'controller.clearMediaItems()' in main
assert 'onStop = { stopMusic() }' in main and 'onStop: () -> Unit' in full
assert 'if (fullscreen && !showChannels)' in tv
for item in ('RESIZE_MODE_ZOOM','RESIZE_MODE_FILL','ORIGINAL','FIT','CROP','STRETCH','RATIO169','RATIO43'):
    assert item in tv, item
assert 'resolvePlayableAlbumTrack(' in resolver
assert 'audiusTracks' not in main
print("MusicFlow v0.7.5 static regression checks: PASS")
