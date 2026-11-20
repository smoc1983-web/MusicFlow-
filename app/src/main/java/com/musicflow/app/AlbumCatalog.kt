package com.musicflow.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal data class AlbumSong(val number: Int, val title: String, val artist: String, val durationMs: Long)
internal data class AlbumListing(
    val title: String, val artist: String, val releaseId: String, val tracks: List<AlbumSong>
)

/**
 * Keyless public album metadata from Apple iTunes Search and Deezer APIs.
 * This class NEVER uses their previews as music streams. Audio remains sourced from
 * Muzofond and Mail.ru; only verified artist/track matches add album metadata.
 */
internal object AlbumCatalog {
    private const val CACHE_TTL = 7L * 24L * 3600_000L
    private val albumCache = ConcurrentHashMap<String, AlbumListing>()

    private data class MetadataSong(
        val artist: String, val title: String, val album: String,
        val albumId: String, val cover: String?, val durationMs: Long
    )

    private fun norm(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("(?i)\\b(feat\\.?|ft\\.?|featuring)\\b.*$"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun sameSong(wanted: String, actual: String): Boolean {
        val a = norm(wanted)
        val b = norm(actual)
        return a.isNotEmpty() && a == b
    }

    private fun sameArtist(wanted: String, actual: String): Boolean {
        val a = norm(wanted)
        val b = norm(actual)
        if (a.isBlank() || b.isBlank() || a == "неизвестный исполнитель") return false
        if (a == b) return true
        // Matching one of several credited performers is useful for Russian
        // duets; check word boundaries, never a bare character substring.
        val parts = wanted.split(',', '&', ';', '/')
            .map(::norm).filter { it.length >= 3 }
        if (parts.any { it == b }) return true
        return (a.length >= 4 && b.length >= 4 &&
            ((" " + a + " ").contains(" " + b + " ") ||
             (" " + b + " ").contains(" " + a + " ")))
    }

    private fun enc(text: String): String = URLEncoder.encode(text.trim(), "UTF-8")

    private fun get(url: String): JSONObject {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6500
            readTimeout = 8500
            instanceFollowRedirects = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Accept-Language", "ru-RU,ru;q=0.9,en;q=0.8")
            setRequestProperty(
                "User-Agent",
                "MusicFlow/0.7.8 (https://github.com/smoc1983-web/MusicFlow-)"
            )
        }
        try {
            val status = conn.responseCode
            if (status !in 200..299) throw IOException("HTTP $status")
            val reader = conn.inputStream.bufferedReader(Charsets.UTF_8)
            val result = StringBuilder()
            reader.use {
                val chars = CharArray(8192)
                while (result.length < 900_000) {
                    val count = it.read(chars, 0, minOf(chars.size, 900_000 - result.length))
                    if (count < 0) break
                    if (count > 0) result.append(chars, 0, count)
                }
            }
            JSONObject(result.toString())
        } finally { conn.disconnect() }
    }

    // Each HTTP request runs only from a coroutine IO dispatcher.
    private suspend fun getJson(url: String): JSONObject = withContext(Dispatchers.IO) { get(url) }

    private suspend fun appleSearch(query: String, limit: Int = 100): List<MetadataSong> {
        val data = getJson(
            "https://itunes.apple.com/search?term=" + enc(query) +
                "&media=music&entity=song&country=US&limit=$limit"
        )
        val array = data.optJSONArray("results") ?: JSONArray()
        return (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            if (item.optString("kind") != "song") return@mapNotNull null
            val albumId = item.optLong("collectionId", 0L)
            val album = item.optString("collectionName").trim()
            val title = item.optString("trackName").trim()
            val artist = item.optString("artistName").trim()
            if (albumId <= 0 || album.isEmpty() || title.isEmpty() || artist.isEmpty())
                return@mapNotNull null
            MetadataSong(
                artist = artist, title = title, album = album,
                albumId = "itunes:$albumId",
                cover = item.optString("artworkUrl100")
                    .takeIf { it.startsWith("https://") }
                    ?.replace("100x100bb", "600x600bb"),
                durationMs = item.optLong("trackTimeMillis", 0L)
            )
        }
    }

    private suspend fun deezerSearch(query: String, limit: Int = 100): List<MetadataSong> {
        val data = getJson(
            "https://api.deezer.com/search?q=" + enc(query) + "&limit=$limit"
        )
        return data.optJSONArray("data")?.let { array ->
            (0 until array.length()).mapNotNull { index ->
                val item = array.optJSONObject(index) ?: return@mapNotNull null
                val album = item.optJSONObject("album") ?: return@mapNotNull null
                val albumId = album.optLong("id", 0)
                val albumName = album.optString("title").trim()
                val title = item.optString("title_short")
                    .ifBlank { item.optString("title") }.trim()
                val artist = item.optJSONObject("artist")?.optString("name").orEmpty().trim()
                if (albumId <= 0 || albumName.isEmpty() || title.isEmpty() || artist.isEmpty())
                    return@mapNotNull null
                MetadataSong(
                    artist = artist, title = title, album = albumName,
                    albumId = "deezer:$albumId",
                    cover = album.optString("cover_big")
                        .takeIf { it.startsWith("https://") },
                    durationMs = item.optLong("duration", 0L) * 1000L
                )
            }
        } ?: emptyList()
    }

    private suspend fun combinedSearch(query: String, limit: Int): List<MetadataSong> =
        coroutineScope {
            val apple = async { runCatching { appleSearch(query, limit) }.getOrDefault(emptyList()) }
            val deezer = async { runCatching { deezerSearch(query, limit) }.getOrDefault(emptyList()) }
            apple.await() + deezer.await()
        }

    /**
     * One iTunes and one Deezer request enrich an entire search result list.
     * No unbounded per-track crawling and no MusicBrainz dependency.
     */
    suspend fun enrichTracks(context: Context, tracks: List<Track>, query: String): List<Track> {
        if (tracks.isEmpty()) return tracks
        val prefs = context.applicationContext.getSharedPreferences("album_metadata_v2", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        val cached = tracks.map { track ->
            if (!track.collectionTitle.isNullOrBlank()) track
            else {
                val id = norm(track.artist) + "|" + norm(track.title)
                val stored = prefs.getString("v:" + id.hashCode() + ":" + id.length, null)
                val expires = prefs.getLong("t:" + id.hashCode() + ":" + id.length, 0L)
                if (stored != null && expires > now && stored.isNotBlank())
                    track.copy(collectionTitle = stored)
                else track
            }
        }
        val missing = cached.filter { it.collectionTitle.isNullOrBlank() }
        if (missing.isEmpty()) return cached
        if (query.isBlank()) return cached
        val metadata = combinedSearch(query, 200)
        if (metadata.isEmpty()) return cached

        var matched = 0
        val result = cached.map { track ->
            if (!track.collectionTitle.isNullOrBlank()) track
            else {
                val candidate = metadata.firstOrNull {
                    sameSong(track.title, it.title) && sameArtist(track.artist, it.artist)
                }
                if (candidate == null) track
                else {
                    matched++
                    val id = norm(track.artist) + "|" + norm(track.title)
                    prefs.edit().putString(
                        "v:" + id.hashCode() + ":" + id.length, candidate.album
                    ).putLong(
                        "t:" + id.hashCode() + ":" + id.length, now + CACHE_TTL
                    ).apply()
                    track.copy(
                        collectionTitle = candidate.album,
                        artwork = track.artwork ?: candidate.cover
                    )
                }
            }
        }
        AppDiagnostics.info("Album", "metadata enriched; total=" + tracks.size +
            "; matched=" + matched)
        return result
    }

    /**
     * Load a verified full album tracklist from Apple, falling back to Deezer.
     * The selected recording must actually be present in the album listing.
     */
    suspend fun lookup(artist: String, trackTitle: String, albumHint: String?): AlbumListing? {
        val key = norm(artist) + "|" + norm(trackTitle) + "|" + norm(albumHint.orEmpty())
        albumCache[key]?.let { return it }
        val candidates = combinedSearch("$artist $trackTitle", 25)
            .filter { sameSong(trackTitle, it.title) && sameArtist(artist, it.artist) }
            .sortedWith(
                compareByDescending<MetadataSong> {
                    !albumHint.isNullOrBlank() && norm(it.album) == norm(albumHint)
                }.thenBy { if (it.album.lowercase(Locale.ROOT).contains("single")) 1 else 0 }
            )
        if (candidates.isEmpty()) return null
        for (song in candidates.distinctBy { it.albumId }.take(8)) {
            val album = runCatching {
                if (song.albumId.startsWith("itunes:"))
                    appleAlbum(song.albumId.substringAfter(':'), trackTitle)
                else deezerAlbum(song.albumId.substringAfter(':'), trackTitle)
            }.getOrNull()
            if (album != null) {
                albumCache[key] = album
                return album
            }
        }
        return null
    }

    private suspend fun appleAlbum(id: String, selectedTitle: String): AlbumListing? {
        if (!id.all(Char::isDigit)) return null
        val data = getJson(
            "https://itunes.apple.com/lookup?id=$id&entity=song&country=US&limit=200"
        )
        val array = data.optJSONArray("results") ?: return null
        var title = ""
        var artist = ""
        data class Entry(val disc: Int, val order: Int, val song: AlbumSong)
        val tracks = mutableListOf<Entry>()
        for (i in 0 until array.length()) {
            val item = array.optJSONObject(i) ?: continue
            if (item.optString("wrapperType") == "collection") {
                title = item.optString("collectionName")
                artist = item.optString("artistName")
            } else if (item.optString("wrapperType") == "track" &&
                item.optString("kind") == "song") {
                val name = item.optString("trackName")
                if (name.isBlank()) continue
                val singer = item.optString("artistName").ifBlank { artist }
                tracks += Entry(
                    item.optInt("discNumber", 1),
                    item.optInt("trackNumber", tracks.size + 1),
                    AlbumSong(tracks.size + 1, name, singer, item.optLong("trackTimeMillis"))
                )
            }
        }
        if (tracks.size < 2 || tracks.none { sameSong(it.song.title, selectedTitle) }) return null
        return AlbumListing(
            title = title.ifBlank { tracks.first().song.title },
            artist = artist,
            releaseId = "itunes:$id",
            tracks = tracks.sortedWith(compareBy(Entry::disc, Entry::order))
                .mapIndexed { index, entry -> entry.song.copy(number = index + 1) }
        )
    }

    private suspend fun deezerAlbum(id: String, selectedTitle: String): AlbumListing? {
        if (!id.all(Char::isDigit)) return null
        val data = getJson("https://api.deezer.com/album/$id")
        val array = data.optJSONObject("tracks")?.optJSONArray("data") ?: return null
        val artist = data.optJSONObject("artist")?.optString("name").orEmpty()
        val tracks = (0 until array.length()).mapNotNull { i ->
            val track = array.optJSONObject(i) ?: return@mapNotNull null
            val name = track.optString("title_short")
                .ifBlank { track.optString("title") }
            if (name.isBlank()) return@mapNotNull null
            AlbumSong(
                number = i + 1,
                title = name,
                artist = track.optJSONObject("artist")?.optString("name")
                    ?.ifBlank { artist } ?: artist,
                durationMs = track.optLong("duration", 0L) * 1000L
            )
        }
        if (tracks.size < 2 || tracks.none { sameSong(it.title, selectedTitle) }) return null
        return AlbumListing(data.optString("title"), artist, "deezer:$id", tracks)
    }
}
