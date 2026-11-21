package com.musicflow.app

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps MusicFlow's queue, shuffle/repeat settings, local history and per-track
 * progress. Does not collect identifiers or send listening data to a server.
 * The playback service, not the Compose screen, owns persistence so that music
 * is preserved across UI process recreation and background playback.
 */
internal class PlaybackJournal(context: Context, private val player: ExoPlayer) : Player.Listener {
    private val application = context.applicationContext
    private val preferences = application
        .getSharedPreferences("musicflow_player_state_v1", Context.MODE_PRIVATE)
    private val listened = ListeningProgressTracker()
    private var queueDirty = true

    private fun playable(uri: String, id: String): Boolean =
        (uri.startsWith("https://") || uri.startsWith("http://") ||
            (id.startsWith("zaycev:") && Regex("""zaycev://track/\d{1,16}""").matches(uri))) &&
            !id.startsWith("vk:")

    internal fun resumePosition(id: String, durationMs: Long): Long {
        if (id.startsWith("radio:") || id.startsWith("ruvideo:") || durationMs <= 0L) return 0L
        val pos = preferences.getLong("resume_" + id.take(160), 0L)
        return pos.takeIf { it in 10_000L until (durationMs - 10_000L).coerceAtLeast(0L) } ?: 0L
    }

    fun restore() {
        runCatching {
            val array = JSONArray(preferences.getString("queue", "[]"))
            val queue = (0 until minOf(array.length(), 80)).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val id = obj.optString("id")
                val uri = obj.optString("uri")
                if (id.isBlank() || !playable(uri, id)) return@mapNotNull null
                val extra = Bundle().apply {
                    putString(MUSIC_STREAMS, obj.optString("alternatives"))
                    putString(MUSIC_SOURCE, obj.optString("source", id.substringBefore(':')))
                    putLong(MUSIC_DURATION_SECONDS, obj.optLong("duration"))
                    putBoolean(MUSIC_HOME_PLAYLIST, obj.optBoolean("home"))
                }
                MediaItem.Builder()
                    .setMediaId(id)
                    .setUri(uri)
                    .setMediaMetadata(MediaMetadata.Builder()
                        .setTitle(obj.optString("title").take(180))
                        .setArtist(obj.optString("artist").take(180))
                        .setAlbumTitle(obj.optString("album").takeIf { it.isNotBlank() })
                        .setArtworkUri(obj.optString("artwork")
                            .takeIf { it.startsWith("https://") }?.let(Uri::parse))
                        .setExtras(extra)
                        .build())
                    .build()
            }
            player.repeatMode = preferences.getInt("repeat", Player.REPEAT_MODE_OFF)
                .takeIf { it in Player.REPEAT_MODE_OFF..Player.REPEAT_MODE_ALL }
                ?: Player.REPEAT_MODE_OFF
            player.shuffleModeEnabled = preferences.getBoolean("shuffle", false)
            if (queue.isNotEmpty()) {
                val selected = preferences.getInt("index", 0).coerceIn(0, queue.lastIndex)
                val id = queue[selected].mediaId
                val position = resumePosition(
                    id, queue[selected].mediaMetadata.extras?.getLong(MUSIC_DURATION_SECONDS, 0L)?.times(1000L) ?: 0L
                )
                player.setMediaItems(queue, selected, position)
                // Intentionally no autoplay on app start. The first user Play
                // presses Prepare before Play when the service is still IDLE.
            }
        }.onFailure { AppDiagnostics.failure("Journal", "restore queue", it) }
    }

    /**
     * The service polls this method during playback. Queue JSON is only serialized
     * when Media3's timeline changes; the position is persisted on every tick.
     */
    fun persist() {
        runCatching {
            val current = player.currentMediaItem
            val tick = listened.sample(
                current?.mediaId,
                SystemClock.elapsedRealtime(),
                player.currentPosition.coerceAtLeast(0L),
                player.isPlaying
            )
            if (tick != null && current != null) {
                runCatching {
                    if (tick.qualifiedStart) appendHistory(current)
                    ListeningStatisticsStore.record(application, current, tick)
                }.onFailure { AppDiagnostics.failure("Journal", "listening stats", it) }
            }

            val editor = preferences.edit()
            if (queueDirty) {
                val queue = JSONArray()
                for (index in 0 until minOf(player.mediaItemCount, 80)) {
                    val item = player.getMediaItemAt(index)
                    val id = item.mediaId
                    val uri = item.localConfiguration?.uri?.toString().orEmpty()
                    if (!playable(uri, id)) continue
                    val extras = item.mediaMetadata.extras
                    queue.put(JSONObject().apply {
                        put("id", id)
                        put("uri", uri)
                        put("title", item.mediaMetadata.title?.toString().orEmpty())
                        put("artist", item.mediaMetadata.artist?.toString().orEmpty())
                        put("album", item.mediaMetadata.albumTitle?.toString().orEmpty())
                        put("artwork", item.mediaMetadata.artworkUri?.toString().orEmpty())
                        put("duration", extras?.getLong(MUSIC_DURATION_SECONDS, 0L) ?: 0L)
                        put("alternatives", extras?.getString(MUSIC_STREAMS).orEmpty())
                        put("source", extras?.getString(MUSIC_SOURCE).orEmpty())
                        put("home", extras?.getBoolean(MUSIC_HOME_PLAYLIST, false) ?: false)
                    })
                }
                editor.putString("queue", queue.toString())
                queueDirty = false
            }

            editor.putInt("index", player.currentMediaItemIndex.coerceAtLeast(0))
                .putBoolean("shuffle", player.shuffleModeEnabled)
                .putInt("repeat", player.repeatMode)

            val pos = player.currentPosition.coerceAtLeast(0L)
            val duration = player.duration.coerceAtLeast(0L)
            if (current != null && !current.mediaId.startsWith("radio:") &&
                !current.mediaId.startsWith("ruvideo:") && duration > 0L) {
                editor.putLong("resume_" + current.mediaId.take(160),
                    if (player.playbackState == Player.STATE_ENDED || pos >= duration - 4_000L) 0L else pos)
                editor.putLong("length_" + current.mediaId.take(160), duration)
            }
            editor.apply()
        }.onFailure { AppDiagnostics.failure("Journal", "persist queue and position", it) }
    }

    override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
        queueDirty = true
        persist()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = persist()

    override fun onIsPlayingChanged(isPlaying: Boolean) = persist()

    override fun onRepeatModeChanged(repeatMode: Int) = persist()
    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = persist()
    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED || playbackState == Player.STATE_READY) persist()
    }

    private fun appendHistory(item: MediaItem) {
        val uri = item.localConfiguration?.uri?.toString().orEmpty()
        if (!playable(uri, item.mediaId)) return
        val entry = JSONObject().apply {
            put("id", item.mediaId)
            put("uri", uri)
            put("title", item.mediaMetadata.title?.toString().orEmpty())
            put("artist", item.mediaMetadata.artist?.toString().orEmpty())
            put("artwork", item.mediaMetadata.artworkUri?.toString().orEmpty())
            put("duration", item.mediaMetadata.extras?.getLong(MUSIC_DURATION_SECONDS, 0L) ?: 0L)
            put("alternatives", item.mediaMetadata.extras?.getString(MUSIC_STREAMS).orEmpty())
            put("at", System.currentTimeMillis())
        }
        val existing = runCatching { JSONArray(preferences.getString("history", "[]")) }
            .getOrDefault(JSONArray())
        val list = JSONArray().put(entry)
        for (i in 0 until existing.length()) {
            val old = existing.optJSONObject(i) ?: continue
            if (old.optString("id") != item.mediaId) list.put(old)
            if (list.length() >= 100) break
        }
        preferences.edit().putString("history", list.toString()).apply()
    }

    companion object {
        fun resumePosition(context: Context, id: String, durationSec: Long): Long {
            if (durationSec <= 0 || id.startsWith("radio:") || id.startsWith("ruvideo:")) return 0L
            val pos = context.getSharedPreferences("musicflow_player_state_v1", Context.MODE_PRIVATE)
                .getLong("resume_" + id.take(160), 0L)
            val length = durationSec * 1000L
            return pos.takeIf { it >= 10_000L && pos < length - 10_000L } ?: 0L
        }

        fun history(context: Context): List<Track> = runCatching {
            val text = context.getSharedPreferences("musicflow_player_state_v1", Context.MODE_PRIVATE)
                .getString("history", "[]")
            val arr = JSONArray(text)
            (0 until arr.length()).mapNotNull { index ->
                val item = arr.optJSONObject(index) ?: return@mapNotNull null
                val id = item.optString("id")
                val uri = item.optString("uri")
                if (id.startsWith("vk:") || !(uri.startsWith("https://") ||
                        uri.startsWith("http://") || uri.startsWith("zaycev://track/"))) return@mapNotNull null
                Track(
                    id = id,
                    title = item.optString("title"),
                    artist = item.optString("artist"),
                    artwork = item.optString("artwork").takeIf { it.startsWith("https://") },
                    stream = uri,
                    durationSec = item.optLong("duration"),
                    alternatives = decodeStreams(item.optString("alternatives"))
                )
            }
        }.getOrDefault(emptyList())

        fun clearHistory(context: Context) {
            context.getSharedPreferences("musicflow_player_state_v1", Context.MODE_PRIVATE)
                .edit().remove("history").apply()
        }
    }
}
