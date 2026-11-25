package com.musicflow.app

import java.util.Locale

internal enum class SearchMode(val label: String) {
    ALL("Везде"),
    ARTIST("Исполнитель"),
    TITLE("Название")
}

internal enum class SortMode(val label: String) {
    RELEVANCE("Совпадение"),
    TITLE("Песня А–Я"),
    ARTIST("Исполнитель А–Я"),
    DURATION("Длительность ↓")
}

internal fun filterAndSortTracks(
    tracks: List<Track>,
    query: String,
    searchMode: SearchMode,
    sortMode: SortMode
): List<Track> {
    val q = query.trim().lowercase(Locale.ROOT)
    val filtered = if (q.isBlank() || searchMode == SearchMode.ALL) {
        tracks
    } else {
        tracks.filter { track ->
            when (searchMode) {
                SearchMode.ARTIST -> track.artist.lowercase(Locale.ROOT).contains(q)
                SearchMode.TITLE -> track.title.lowercase(Locale.ROOT).contains(q) ||
                    (track.collectionTitle?.lowercase(Locale.ROOT)?.contains(q) == true)
                SearchMode.ALL -> true
            }
        }
    }
    return when (sortMode) {
        SortMode.RELEVANCE -> if (q.isBlank()) filtered else filtered.sortedWith(
            compareByDescending<Track> { relevanceScore(it, q, searchMode) }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        )
        SortMode.TITLE -> filtered.sortedWith(
            compareBy<Track> { it.title.lowercase(Locale.ROOT) }
                .thenBy { it.artist.lowercase(Locale.ROOT) }
        )
        SortMode.ARTIST -> filtered.sortedWith(
            compareBy<Track> { it.artist.lowercase(Locale.ROOT) }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        )
        SortMode.DURATION -> filtered.sortedWith(
            compareByDescending<Track> { it.durationSec }
                .thenBy { it.title.lowercase(Locale.ROOT) }
        )
    }
}

private fun relevanceScore(track: Track, q: String, mode: SearchMode): Int {
    val fields = when (mode) {
        SearchMode.ARTIST -> listOf(track.artist)
        SearchMode.TITLE -> listOf(track.title, track.collectionTitle.orEmpty())
        SearchMode.ALL -> listOf(track.title, track.artist, track.collectionTitle.orEmpty())
    }
    return fields.mapIndexed { index, text ->
        val normalized = text.lowercase(Locale.ROOT)
        val weight = if (index == 0) 3 else 1
        when {
            normalized == q -> 100 * weight
            normalized.startsWith(q) -> 40 * weight
            normalized.contains(q) -> 10 * weight
            else -> 0
        }
    }.maxOrNull() ?: 0
}

internal fun formatTrackDuration(seconds: Long): String {
    if (seconds <= 0) return ""
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remaining = seconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, remaining)
           else "%d:%02d".format(minutes, remaining)
}
