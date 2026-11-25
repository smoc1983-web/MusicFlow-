package com.musicflow.app

/** Pure reducer, kept free of Android IO so time totals and play counts can be unit tested. */
internal data class TrackListeningSummary(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String?,
    val listenedMs: Long,
    val playCount: Int,
    val lastHeardAtMs: Long
)

internal object ListeningStatisticsReducer {
    fun record(
        items: List<TrackListeningSummary>,
        id: String,
        title: String,
        artist: String,
        artwork: String?,
        deltaMs: Long,
        qualifiedStart: Boolean,
        nowMs: Long
    ): List<TrackListeningSummary> {
        if (id.isBlank() || deltaMs <= 0L) return items
        val old = items.firstOrNull { it.id == id }
        val updated = TrackListeningSummary(
            id = id.take(180),
            title = title.ifBlank { "Без названия" }.take(180),
            artist = artist.ifBlank { "Неизвестный исполнитель" }.take(180),
            artwork = artwork?.takeIf { it.startsWith("https://") }?.take(1000),
            listenedMs = ((old?.listenedMs ?: 0L) + deltaMs).coerceAtLeast(0L),
            playCount = ((old?.playCount ?: 0) + if (qualifiedStart) 1 else 0).coerceAtMost(1_000_000),
            lastHeardAtMs = nowMs
        )
        return (items.filterNot { it.id == id } + updated)
            .sortedWith(compareByDescending<TrackListeningSummary> { it.listenedMs }
                .thenByDescending { it.lastHeardAtMs })
            .take(200)
    }
}
