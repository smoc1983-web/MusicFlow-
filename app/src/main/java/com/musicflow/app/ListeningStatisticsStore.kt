package com.musicflow.app

import android.content.Context
import androidx.media3.common.MediaItem
import org.json.JSONArray
import org.json.JSONObject

/** All listening insights remain on this device. Does not query a remote service. */
internal object ListeningStatisticsStore {
    private const val PREFS = "musicflow_listening_statistics_v1"
    private const val KEY = "records"

    fun entries(context: Context): List<TrackListeningSummary> = runCatching {
        val stored = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]").orEmpty().take(250_000)
        val data = JSONArray(stored)
        (0 until minOf(data.length(), 200)).mapNotNull { index ->
            val item = data.optJSONObject(index) ?: return@mapNotNull null
            val id = item.optString("id").take(180)
            if (id.isBlank()) return@mapNotNull null
            TrackListeningSummary(
                id = id,
                title = item.optString("title").take(180),
                artist = item.optString("artist").take(180),
                artwork = item.optString("artwork").takeIf { it.startsWith("https://") }?.take(1000),
                listenedMs = item.optLong("listened_ms", 0L).coerceAtLeast(0L),
                playCount = item.optInt("play_count", 0).coerceAtLeast(0),
                lastHeardAtMs = item.optLong("last_heard", 0L).coerceAtLeast(0L)
            )
        }.sortedByDescending { it.listenedMs }
    }.getOrDefault(emptyList())

    fun record(context: Context, item: MediaItem, tick: ListeningTick) {
        if (item.mediaId != tick.id) return
        val metadata = item.mediaMetadata
        val old = entries(context)
        val updated = ListeningStatisticsReducer.record(
            old, tick.id,
            metadata.title?.toString().orEmpty(),
            metadata.artist?.toString().orEmpty(),
            metadata.artworkUri?.toString(),
            tick.listenedMs, tick.qualifiedStart, System.currentTimeMillis()
        )
        val arr = JSONArray()
        updated.forEach { stat ->
            arr.put(JSONObject().apply {
                put("id", stat.id)
                put("title", stat.title)
                put("artist", stat.artist)
                put("artwork", stat.artwork.orEmpty())
                put("listened_ms", stat.listenedMs)
                put("play_count", stat.playCount)
                put("last_heard", stat.lastHeardAtMs)
            })
        }
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().remove(KEY).apply()
    }
}
