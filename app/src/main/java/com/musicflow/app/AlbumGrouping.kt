package com.musicflow.app

import java.util.Locale

/**
 * Source-neutral album grouping for any track with known collectionTitle.
 * Tracks with no verified album tag remain separate; no album names are guessed.
 */
internal data class AlbumTrackGroup(
    val id: String,
    val albumTitle: String?,
    val artist: String?,
    val songs: List<Track>
)

internal fun groupTracksByAlbum(tracks: List<Track>): List<AlbumTrackGroup> {
    if (tracks.isEmpty()) return emptyList()
    val items = LinkedHashMap<String, MutableList<Track>>()
    val albumNames = HashMap<String, Pair<String?, String?>>()
    val singles = "__without_album__"
    for (track in tracks) {
        val album = track.collectionTitle?.trim().orEmpty()
        val artist = track.artist.trim()
        val key = if (album.isBlank()) singles
        else "album:" + album.lowercase(Locale.ROOT) + "|" + artist.lowercase(Locale.ROOT)
        items.getOrPut(key) { mutableListOf() }.add(track)
        if (album.isNotBlank()) albumNames[key] = album to artist
    }
    // Group albums first, then singles without unreliable metadata.
    return items.entries.sortedBy { if (it.key == singles) 1 else 0 }
        .map { (key, songs) ->
            val info = albumNames[key]
            AlbumTrackGroup(key, info?.first, info?.second, songs.toList())
        }
}
