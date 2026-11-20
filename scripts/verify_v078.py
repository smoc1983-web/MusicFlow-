from pathlib import Path
root=Path("app/src/main/java/com/musicflow/app")
main=(root/"MainActivity.kt").read_text(encoding="utf-8")
album=(root/"AlbumCatalog.kt").read_text(encoding="utf-8")
home=(root/"ZaycevHome.kt").read_text(encoding="utf-8")
group=(root/"AlbumGrouping.kt").read_text(encoding="utf-8")
player=(root/"FullPlayerDialog.kt").read_text(encoding="utf-8")
assert 'AlbumCatalog.enrichTracks(context, batch, requested)' in main
assert 'AlbumCatalog.enrichTracks(context, items, artist)' in main
assert 'groupTracksByAlbum(visible)' in main
assert 'collectionTitle = original.collectionTitle' in main
assert 'collectionTitle = candidate.album' in album
assert 'itunes.apple.com/search' in album
assert 'itunes.apple.com/lookup' in album
assert 'api.deezer.com/search' in album
assert 'api.deezer.com/album/' in album
assert 'musicbrainz.org/ws/2/' not in album
assert 'AlbumCatalog.lookup(artist, title, albumHint)' in player
assert 'if (found != null) albumInfo = found' in player
assert 'Подборки Zaycev' in home
assert home.index('Подборки Zaycev') < home.index('Text("Жанры"')
assert 'Тематические подборки' not in home
assert 'collectionTitle?.trim()' in group
assert 'audiusTracks(' not in main
print("MusicFlow v0.7.8 metadata enrichment and Zaycev sorting checks: PASS")
