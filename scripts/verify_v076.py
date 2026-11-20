from pathlib import Path
root=Path("app/src/main/java/com/musicflow/app")
iptv=(root/"UserIptvSources.kt").read_text()
tv=(root/"RuVideoHubTv.kt").read_text()
activity=(root/"TvFullscreenActivity.kt").read_text()
full=(root/"FullPlayerDialog.kt").read_text()
main=(root/"MainActivity.kt").read_text()
album=(root/"AlbumCatalog.kt").read_text()
manifest=Path("app/src/main/AndroidManifest.xml").read_text()
assert "PLAYLIST, CHANNEL, FILE" in iptv
assert "importLocalIptvPlaylist" in iptv
assert "OpenDocument()" in tv
assert "TvFullscreenActivity::class.java" in tv
assert "TvPlayerDialog(" not in tv
assert "TvFullscreenActivity" in manifest
assert "WindowCompat.setDecorFitsSystemWindows(window, false)" in activity
assert 'Text("Размер")' in activity
assert 'Text("Качество")' in activity
for mode in ("ORIGINAL", "FIT", "COVER", "STRETCH", "RATIO169", "RATIO43"):
    assert mode in activity
assert "onPlayAlbumTrack" in full
assert ".setAlbumTitle(t.collectionTitle)" in main
assert "AlbumCatalog.lookup" in full
assert "itunes.apple.com" in album and "api.deezer.com" in album
assert "musicbrainz.org/ws/2/" not in album
print("MusicFlow v0.7.6 feature integration checks: PASS")
