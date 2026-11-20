package com.musicflow.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale

/**
 * Resolves a home recommendation to a matching playable MP3.
 * Both providers are queried without opening the search screen.
 * Never starts an unrelated song when the selected recording is unavailable.
 */
internal suspend fun resolveRecommendedTrack(artist: String, title: String): Track? = coroutineScope {
    val results = Channel<Track?>(capacity = 2)
    val firstArtist = artist.substringBefore(',').substringBefore('&')
        .trim().replace(Regex("(?i)^by\\s+"), "")
    val query = listOf(firstArtist, title).filter { it.isNotBlank() }.joinToString(" ")
    val workers = listOf<suspend (String) -> List<Track>>(
        { term -> muzofondTracks(term) },
        { term -> mailRuTracks(term) }
    ).mapIndexed { index, search ->
        launch(Dispatchers.IO) {
            var best: Track? = null
            try {
                val initial = search(query)
                best = bestRecommendation(initial, artist, title)
                if (best == null && query != title) {
                    best = bestRecommendation(search(title), artist, title)
                }
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (e: Exception) {
                AppDiagnostics.failure("Home", "direct playback resolver " + index, e)
            } finally {
                results.trySend(best)
            }
        }
    }
    try {
        withTimeoutOrNull(20_000L) {
            repeat(workers.size) {
                val candidate = results.receive()
                if (candidate != null) return@withTimeoutOrNull candidate
            }
            null
        }
    } finally {
        workers.forEach { it.cancel() }
        results.close()
    }
}

private fun homeNormalized(text: String): String =
    text.lowercase(Locale.ROOT).replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim().replace(Regex("\\s+"), " ")

private fun bestRecommendation(found: List<Track>, artist: String, title: String): Track? {
    val wantedTitle = homeNormalized(title)
    if (wantedTitle.isBlank()) return null
    val artists = artist.split(',', '&', '/', ';')
        .map { homeNormalized(it).removePrefix("by ") }
        .filter { it.length >= 3 }
    return found.asSequence().mapNotNull { candidate ->
        val song = homeNormalized(candidate.title)
        // A remix/cover can be a different recording; require exact song name.
        if (song != wantedTitle) return@mapNotNull null
        val performer = homeNormalized(candidate.artist)
        val matchedArtist = artists.any { name ->
            performer == name || performer.contains(name) || name.contains(performer)
        }
        if (!matchedArtist) return@mapNotNull null
        val score = 100 + if (performer == homeNormalized(artist)) 20 else 0
        score to candidate
    }.maxByOrNull { it.first }?.second
}
