package com.musicflow.app

import android.content.Context
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Semaphore

/** Best-effort public cover lookup, strictly separate from audio playback. */
internal object CoverLookup {
    private const val MISSING = "_missing_"
    private val cache = ConcurrentHashMap<String, String>()
    private val semaphore = Semaphore(2)

    /** Ask known artwork CDNs for their documented high-resolution rendition. */
    internal fun preferredProvided(url: String?): String? {
        val original = url?.takeIf { it.startsWith("https://") } ?: return null
        return when {
            original.contains("mzstatic.com", ignoreCase = true) ->
                original.replace(Regex("[0-9]{2,4}x[0-9]{2,4}bb"), "1200x1200bb")
            original.contains("coverartarchive.org", ignoreCase = true) ->
                original.replace("/front-250", "/front-1200")
            else -> original
        }
    }

    private fun needsUpgrade(url: String?): Boolean = url == null ||
        url.contains("zaycev.net", ignoreCase = true) ||
        url.contains("thumb", ignoreCase = true) ||
        url.contains("100x100", ignoreCase = true) ||
        url.contains("front-250", ignoreCase = true)
    private fun normalized(s: String) = s.lowercase(Locale.ROOT)
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
        .replace(Regex("\\s+"), " ")

    suspend fun resolve(context: Context, artist: String, title: String, provided: String?): String? {
        val original = preferredProvided(provided)
        if (!needsUpgrade(provided) || artist.isBlank() || title.isBlank()) return original
        val key = normalized(artist) + "|" + normalized(title)
        cache[key]?.let { return it.takeUnless { v -> v == MISSING } ?: original }
        return withContext(Dispatchers.IO) {
            val prefs = context.applicationContext
                .getSharedPreferences("musicflow_cover_cache", Context.MODE_PRIVATE)
            val id = key.hashCode().toString() + "_" + key.length
            if (prefs.getLong("exp_" + id, 0L) > System.currentTimeMillis()) {
                val old = prefs.getString(id, MISSING) ?: MISSING
                cache[key] = old
                return@withContext old.takeUnless { it == MISSING } ?: original
            }
            semaphore.acquire()
            try {
                cache[key]?.let { return@withContext it.takeUnless { v -> v == MISSING } ?: original }
                val found = runCatching { iTunes(artist, title) }.getOrNull()
                    ?: runCatching { musicBrainz(artist, title) }.getOrNull()
                cache[key] = found ?: MISSING
                prefs.edit().putString(id, found ?: MISSING)
                    .putLong("exp_" + id, System.currentTimeMillis() + 7L * 86400_000L).apply()
                found ?: original
            } finally {
                semaphore.release()
            }
        }
    }

    private fun fetch(url: String): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            setRequestProperty("User-Agent", "MusicFlow/0.7.4 (https://github.com/smoc1983-web/MusicFlow-)")
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (conn.responseCode !in 200..299) return ""
            val buffer = StringBuilder()
            conn.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val characters = CharArray(4096)
                while (buffer.length < 220_000) {
                    val n = reader.read(characters, 0, minOf(characters.size, 220_000 - buffer.length))
                    if (n < 0) break
                    if (n > 0) buffer.append(characters, 0, n)
                }
            }
            buffer.toString()
        } finally { conn.disconnect() }
    }

    private fun iTunes(artist: String, title: String): String? {
        val q = URLEncoder.encode("$artist $title", "UTF-8")
        val raw = fetch("https://itunes.apple.com/search?term=$q&media=music&entity=song&limit=8")
        if (raw.isBlank()) return null
        val items = JSONObject(raw).optJSONArray("results") ?: return null
        val wantedTitle = normalized(title)
        val wantedArtist = normalized(artist)
        for (i in 0 until minOf(items.length(), 8)) {
            val item = items.optJSONObject(i) ?: continue
            val t = normalized(item.optString("trackName"))
            val a = normalized(item.optString("artistName"))
            val titleMatch = t == wantedTitle ||
                (wantedTitle.length > 5 && (t.startsWith(wantedTitle + " ") || wantedTitle.startsWith(t + " ")))
            val artistMatch = a == wantedArtist || (a.length >= 4 &&
                (wantedArtist.contains(a) || a.contains(wantedArtist)))
            if (!titleMatch || !artistMatch) continue
            val url = item.optString("artworkUrl100")
            if (url.startsWith("https://")) {
                return url.replace("100x100bb", "1200x1200bb").replace("100x100-75", "1200x1200-75")
            }
        }
        return null
    }

    private fun musicBrainz(artist: String, title: String): String? {
        val terms = URLEncoder.encode("recording:\"" + title.take(80) +
            "\" AND artist:\"" + artist.take(80) + "\"", "UTF-8")
        val raw = fetch("https://musicbrainz.org/ws/2/recording/?query=$terms&fmt=json&limit=3")
        if (raw.isBlank()) return null
        val songs = JSONObject(raw).optJSONArray("recordings") ?: return null
        for (i in 0 until minOf(songs.length(), 3)) {
            val song = songs.optJSONObject(i) ?: continue
            if (song.optInt("score", 0) < 90 ||
                normalized(song.optString("title")) != normalized(title)) continue
            val albums = song.optJSONArray("releases") ?: continue
            for (j in 0 until minOf(albums.length(), 2)) {
                val uuid = albums.optJSONObject(j)?.optString("id").orEmpty()
                if (!Regex("[a-f0-9-]{36}").matches(uuid)) continue
                val image = "https://coverartarchive.org/release/$uuid/front-1200"
                val conn = URL(image).openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 4000
                conn.readTimeout = 4000
                val code = try { conn.responseCode } finally { conn.disconnect() }
                if (code in 200..299) return image
            }
        }
        return null
    }
}

@Composable
internal fun rememberCoverArt(artist: String, title: String, provided: String?): State<String?> {
    val context = LocalContext.current
    return produceState(initialValue = CoverLookup.preferredProvided(provided), artist, title, provided) {
        value = CoverLookup.resolve(context, artist, title, provided)
    }
}
