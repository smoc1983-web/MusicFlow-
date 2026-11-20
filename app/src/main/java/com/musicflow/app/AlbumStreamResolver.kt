package com.musicflow.app

internal suspend fun resolvePlayableAlbumTrack(
    artist: String,
    title: String,
    knownTracks: List<Track>,
    durationSec: Long = 0
): Track? {
    val known = knownTracks.filter { recordingMatches(it, artist, title, durationSec) }
    val matches = known.ifEmpty { findRecording(artist, title, durationSec) }
    return combineMusicTracks(emptyList(), matches).firstOrNull()
}
