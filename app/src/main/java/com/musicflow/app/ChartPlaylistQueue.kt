package com.musicflow.app

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * Chart entries are metadata, never trusted audio URLs. Defer resolving each
 * next/previous entry until Media3 requests its bytes, so opening a top-100
 * chart does not fire one hundred searches on the UI thread.
 */
internal object ChartPlaylistQueue {
    internal data class Lookup(val artist: String, val title: String)

    private fun key(song: TopSong): String = musicKey(song.artist) + "|" + musicKey(song.title)

    internal fun uri(song: TopSong): String {
        val artist = URLEncoder.encode(song.artist.take(200), StandardCharsets.UTF_8.name())
        val title = URLEncoder.encode(song.title.take(240), StandardCharsets.UTF_8.name())
        return "chart://track?artist=$artist&title=$title"
    }

    internal fun parse(raw: String): Lookup? = runCatching {
        val parsed = URI(raw)
        if (parsed.scheme != "chart" || parsed.host != "track" ||
            parsed.path.orEmpty().isNotEmpty() || parsed.fragment != null) return@runCatching null
        val parts = parsed.rawQuery.orEmpty().split('&')
            .filter { it.contains('=') }
            .associate { it.substringBefore('=') to
                URLDecoder.decode(it.substringAfter('='), StandardCharsets.UTF_8.name()) }
        if (parts.size != 2 || parts.keys != setOf("artist", "title")) return@runCatching null
        val artist = parts["artist"].orEmpty().trim()
        val title = parts["title"].orEmpty().trim()
        if (artist.isBlank() || title.isBlank() || artist.length > 200 || title.length > 240 ||
            artist.any { it.code < 32 } || title.any { it.code < 32 }) return@runCatching null
        Lookup(artist, title)
    }.getOrNull()

    internal fun build(
        selected: TopSong,
        chartSection: List<TopSong>,
        selectedPlayable: Track?
    ): HomePlaylistQueue? {
        if (selectedPlayable == null ||
            !recordingMatches(selectedPlayable, selected.artist, selected.title)) return null

        val ordered = (if (chartSection.any { key(it) == key(selected) }) chartSection
            else listOf(selected) + chartSection)
            .distinctBy(::key)
            .take(100)
        val selectedKey = key(selected)
        var index = -1
        val entries = ordered.mapIndexed { position, song ->
            if (key(song) == selectedKey) {
                index = position
                selectedPlayable.copy(
                    title = song.title,
                    artist = song.artist,
                    artwork = song.artwork ?: selectedPlayable.artwork,
                    collectionTitle = "ТОП песен"
                )
            } else {
                // The chart index is stable for this queue; title/artist are
                // persisted separately for accurate source lookup.
                Track(
                    id = "chart:" + song.artist.hashCode().toUInt().toString(16) +
                        ":" + song.title.hashCode().toUInt().toString(16),
                    title = song.title,
                    artist = song.artist,
                    artwork = song.artwork,
                    stream = uri(song),
                    durationSec = 0L,
                    collectionTitle = "ТОП песен"
                )
            }
        }
        return if (index in entries.indices) HomePlaylistQueue(entries, index) else null
    }
}
