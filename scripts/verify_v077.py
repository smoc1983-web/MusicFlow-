from pathlib import Path
root=Path("app/src/main/java/com/musicflow/app")
main=(root/"MainActivity.kt").read_text(encoding="utf-8")
full=(root/"FullPlayerDialog.kt").read_text(encoding="utf-8")
group=(root/"AlbumGrouping.kt").read_text(encoding="utf-8")
catalog=(root/"AlbumCatalog.kt").read_text(encoding="utf-8")
assert "groupTracksByAlbum(visible)" in main
assert "albumGroup.songs.size > 1" in main
assert "collectionTitle = original.collectionTitle" in main
assert "setAlbumTitle(t.collectionTitle)" in main
assert "LaunchedEffect(title, artist)" in full
assert "decorFitsSystemWindows = !immersive" in full
assert "player.hasNextMediaItem()" in full
assert "player.hasPreviousMediaItem()" in full
assert "resolvePlayableAlbumTrack(" in full
assert "onPlayAlbumTrack(track.copy(collectionTitle = album.title))" in full
assert "LazyColumn(Modifier.weight(1f)" in full
assert "verticalScroll(" not in full
assert "Transport controls stay outside every scrollable list" in full
assert "PlayerPage.ALBUM" in full and "PlayerPage.QUEUE" in full
assert "collectionTitle?.trim()" in group
assert "itunes.apple.com" in catalog and "api.deezer.com" in catalog
assert "musicbrainz.org/ws/2/" not in catalog
print("v0.7.7 albums and fixed player controls: static regression PASS")
