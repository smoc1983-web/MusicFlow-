package com.musicflow.app

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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
    // Versioned cache: older builds may have stored small or incorrect images.
    private const val CACHE_VERSION = "hq4_"
    private const val POSITIVE_TTL = 14L * 86400_000L
    private const val NEGATIVE_TTL = 12L * 3600_000L

    /** Prefer native high-resolution endpoints where their URL format is known. */
    internal fun preferredProvided(url: String?): String? {
        val original = url?.takeIf { it.startsWith("https://", ignoreCase = true) } ?: return null
        return when {
            original.contains("mzstatic.com", ignoreCase = true) ->
                original.replace(Regex("""\d{2,4}x\d{2,4}(?:bb|-\d+)"""), "1200x1200bb")
            original.contains("coverartarchive.org", ignoreCase = true) ->
                original.replace(Regex("""/front-(250|500)(?!\d)"""), "/front-1200")
            original.contains("dzcdn.net", ignoreCase = true) ->
                original.replace(Regex("""/(?:56|120|250|500)x(?:56|120|250|500)-"""), "/1000x1000-")
            else -> original
        }
    }

    /**
     * Instant per-recording artwork for Compose. A prefetched high-res URL is
     * available without a network wait; never reuse the previous track's art.
     */
    internal fun cachedOrProvided(artist: String, title: String, provided: String?): String? {
        val key = normalized(artist) + "|" + normalized(title)
        val cached = cache[key]
        return cached?.takeUnless { it == MISSING } ?: preferredProvided(provided)
    }

    private fun needsUpgrade(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val highResolution = preferredProvided(url).orEmpty()
        // A high-resolution endpoint from a trusted music metadata CDN is
        // already suitable. Everything else may only be a tiny site thumbnail.
        return !(
            highResolution.contains("mzstatic.com", true) &&
                highResolution.contains("1200x1200", true) ||
            highResolution.contains("dzcdn.net", true) &&
                highResolution.contains("1000x1000", true) ||
            highResolution.contains("coverartarchive.org", true) &&
                highResolution.contains("/front-1200", true)
        )
    }

    private fun normalized(s: String): String = s.lowercase(Locale.ROOT).replace('ё', 'е')
        .replace(Regex("""[^\p{L}\p{N}]+"""), " ").trim()
        .replace(Regex("""\s+"""), " ")

    /** Credits may list multiple performers, but never accept an unrelated artist. */
    private fun artistMatches(requested: String, actual: String): Boolean {
        val expected = normalized(requested)
        val found = normalized(actual)
        if (expected.isEmpty() || found.isEmpty()) return false
        if (expected == found) return true
        val separators = Regex("""(?i)\s*(?:,|&|/|\bfeat\.?|\bft\.?|\bи\b)\s*""")
        val requestedArtists = requested.split(separators)
            .map(::normalized).filter(String::isNotBlank)
        val candidateArtists = actual.split(separators)
            .map(::normalized).filter(String::isNotBlank)
        return requestedArtists.any { it in candidateArtists }
    }

    suspend fun resolve(context: Context, artist: String, title: String, provided: String?): String? {
        val original = preferredProvided(provided)
        if (!needsUpgrade(provided) || artist.isBlank() || title.isBlank()) return original
        val key = normalized(artist) + "|" + normalized(title)
        cache[key]?.let { return it.takeUnless { v -> v == MISSING } ?: original }
        return withContext(Dispatchers.IO) {
            val prefs = context.applicationContext
                .getSharedPreferences("musicflow_cover_cache", Context.MODE_PRIVATE)
            val id = CACHE_VERSION + key.hashCode().toString() + "_" + key.length
            if (prefs.getLong("exp_" + id, 0L) > System.currentTimeMillis()) {
                val old = prefs.getString(id, MISSING) ?: MISSING
                cache[key] = old
                return@withContext old.takeUnless { it == MISSING } ?: original
            }
            semaphore.acquire()
            try {
                cache[key]?.let { return@withContext it.takeUnless { v -> v == MISSING } ?: original }
                val found = coroutineScope {
                    val apple = async { runCatching { iTunes(artist, title) }.getOrNull() }
                    val deezer = async { runCatching { deezer(artist, title) }.getOrNull() }
                    // Verify real picture dimensions, not just "1200" in a URL:
                    // CDN thumbnails sometimes get upscaled without extra detail.
                    sequenceOf(apple.await(), deezer.await())
                        .filterNotNull().distinct()
                        .firstOrNull { validHighResolution(it) }
                } ?: runCatching { musicBrainz(artist, title) }.getOrNull()
                    ?.takeIf { validHighResolution(it) }
                cache[key] = found ?: MISSING
                prefs.edit().putString(id, found ?: MISSING)
                    .putLong("exp_" + id, System.currentTimeMillis() +
                        if (found == null) NEGATIVE_TTL else POSITIVE_TTL).apply()
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
            setRequestProperty("User-Agent", "MusicFlow/0.7.15 (https://github.com/smoc1983-web/MusicFlow-)")
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
        val wantedTitle = normalized(title)
        // RU first for domestic repertoire; retry the global catalog for
        // international recordings. Metadata endpoints do not provide audio.
        for (region in listOf("RU", "US")) {
            val raw = fetch(
                "https://itunes.apple.com/search?term=$q&media=music&entity=song" +
                    "&country=$region&limit=40"
            )
            if (raw.isBlank()) continue
            val items = JSONObject(raw).optJSONArray("results") ?: continue
            for (i in 0 until minOf(items.length(), 40)) {
                val item = items.optJSONObject(i) ?: continue
                if (normalized(item.optString("trackName")) != wantedTitle) continue
                if (!artistMatches(artist, item.optString("artistName"))) continue
                val url = item.optString("artworkUrl100")
                if (url.startsWith("https://")) return preferredProvided(url)
            }
        }
        return null
    }

    private fun deezer(artist: String, title: String): String? {
        val q = URLEncoder.encode("$artist $title", "UTF-8")
        val raw = fetch("https://api.deezer.com/search?q=$q&limit=30")
        if (raw.isBlank()) return null
        val tracks = JSONObject(raw).optJSONArray("data") ?: return null
        val wantedTitle = normalized(title)
        for (i in 0 until minOf(tracks.length(), 30)) {
            val item = tracks.optJSONObject(i) ?: continue
            val actualTitle = item.optString("title").ifBlank { item.optString("title_short") }
            if (normalized(actualTitle) != wantedTitle) continue
            val actualArtist = item.optJSONObject("artist")?.optString("name").orEmpty()
            if (!artistMatches(artist, actualArtist)) continue
            val album = item.optJSONObject("album") ?: continue
            val candidate = sequenceOf(
                album.optString("cover_xl"),
                album.optString("cover_big"),
                album.optString("cover_medium")
            ).firstOrNull { it.startsWith("https://") } ?: continue
            return preferredProvided(candidate)
        }
        return null
    }

    /**
     * Decode just the HTTP image header, not the whole bitmap. This avoids
     * caching a low-resolution thumbnail as a supposedly HD cover.
     */
    private fun validHighResolution(url: String): Boolean {
        val address = URL(url)
        if (address.protocol != "https") return false
        val connection = (address.openConnection() as HttpURLConnection).apply {
            connectTimeout = 4_000
            readTimeout = 5_000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "MusicFlow/0.7.16")
            setRequestProperty("Accept", "image/jpeg,image/webp,image/png,image/*")
        }
        return try {
            if (connection.responseCode !in 200..299) return false
            val type = connection.contentType.orEmpty().lowercase(Locale.ROOT)
            if (!type.startsWith("image/")) return false
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            connection.inputStream.buffered().use {
                BitmapFactory.decodeStream(it, null, bounds)
            }
            bounds.outWidth >= 500 && bounds.outHeight >= 500
        } catch (_: Exception) {
            false
        } finally {
            connection.disconnect()
        }
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
            val credits = song.optJSONArray("artist-credit") ?: continue
            val verifiedArtist = (0 until credits.length()).any { index ->
                val item = credits.optJSONObject(index) ?: return@any false
                val name = item.optJSONObject("artist")?.optString("name").orEmpty()
                artistMatches(artist, name)
            }
            if (!verifiedArtist) continue
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
    // A remembered produceState keeps the previous track's result while a
    // new key loads. Replacing the state itself clears that stale artwork in
    // the same composition as the title/artist change.
    val result = remember(artist, title, provided) {
        mutableStateOf(CoverLookup.cachedOrProvided(artist, title, provided))
    }
    LaunchedEffect(artist, title, provided) {
        result.value = CoverLookup.resolve(context, artist, title, provided)
    }
    return result
}
