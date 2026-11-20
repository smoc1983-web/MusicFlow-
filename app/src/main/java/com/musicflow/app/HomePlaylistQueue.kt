package com.musicflow.app

/** A Zaycev collection is a playlist, not a studio album. */
internal data class HomePlaylistQueue(
    val tracks: List<Track>,
    val selectedIndex: Int
)

// Every editorial song page has a stable native track ID. The actual stream is
// resolved by ZaycevStreamResolver only when Media3 starts that queue item.
private val zaycevPage = Regex("""^/pages/[0-9]{1,16}/([0-9]{1,16})\.shtml$""")

private fun HomeSong.nativeZaycevTrack(): Track? {
    val numericId = zaycevPage.matchEntire(id)?.groupValues?.get(1) ?: return null
    return Track(
        id = "zaycev:$numericId",
        title = title,
        artist = artist,
        artwork = image,
        stream = "zaycev://track/$numericId",
        durationSec = durationSec
    )
}

/**
 * Preserve the visible order of the chosen collection, popular list or new
 * releases. Do not search every song here: that would create 20-50 blocking
 * network requests and short-lived URLs before the first note is heard.
 *
 * An initial verified match keeps its working provider as the primary stream,
 * with the native Zaycev ID available as a fallback for that same recording.
 */
internal fun buildHomePlaylistQueue(
    selected: HomeSong,
    section: List<HomeSong>,
    selectedPlayable: Track?
): HomePlaylistQueue? {
    val ordered = (if (section.any { it.id == selected.id }) section
                   else listOf(selected) + section).distinctBy { it.id }.take(50)
    val tracks = ArrayList<Track>(ordered.size)
    var selectedIndex = -1
    for (song in ordered) {
        val native = song.nativeZaycevTrack()
        val track = if (song.id == selected.id && selectedPlayable != null) {
            val fallback = native?.let { MusicStream(it.id, it.stream) }
            selectedPlayable.copy(
                artwork = selectedPlayable.artwork?.takeIf { it.isNotBlank() } ?: song.image,
                alternatives = (selectedPlayable.alternatives +
                    listOfNotNull(fallback).filter { it.uri != selectedPlayable.stream })
                    .distinctBy { it.uri }.take(11)
            )
        } else native
        if (track != null && tracks.none { it.id == track.id }) {
            if (song.id == selected.id) selectedIndex = tracks.size
            tracks.add(track)
        }
    }
    return if (selectedIndex >= 0) HomePlaylistQueue(tracks, selectedIndex) else null
}
