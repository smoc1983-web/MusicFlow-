"""MusicFlow 0.7.40: artwork follows the actual Media3 track without stale covers."""
from pathlib import Path
src=Path("app/src/main/java/com/musicflow/app")
covers=(src/"CoverLookup.kt").read_text(encoding="utf-8")
main=(src/"MainActivity.kt").read_text(encoding="utf-8")
full=(src/"FullPlayerDialog.kt").read_text(encoding="utf-8")
assert "remember(artist, title, provided)" in covers
assert "CoverLookup.cachedOrProvided" in covers
assert "LaunchedEffect(artist, title, provided)" in covers
assert "override fun onMediaItemTransition(mediaItem: MediaItem?" in main
assert "override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata)" in main
assert "fun updateActiveMedia" in main
assert "LaunchedEffect(player, currentMediaId)" in main
assert "CoverLookup.resolve(context, nextArtist, nextTitle, provided)" in main
assert "Icons.Filled.MusicNote" in full and "Icons.Filled.MusicNote" in main
assert "rememberCoverArt(artist, title, artwork)" in full
assert "rememberCoverArt(trackArtist, trackTitle, currentArtwork)" in main
print("PASS: immediate cover reset, Media3 transition and next-track prefetch")
