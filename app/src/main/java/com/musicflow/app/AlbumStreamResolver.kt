package com.musicflow.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Album APIs publish metadata, not usable audio URLs.
 * Prefer exact tracks already found in the in-app search/queue and verify the
 * stream before attempting playback. Fall back to fresh native provider results.
 */
internal suspend fun resolvePlayableAlbumTrack(
    artist: String,
    title: String,
    knownTracks: List<Track>
): Track? = withTimeoutOrNull(22_000L) {
    val cached = knownTracks.filter { albumSongMatches(it, artist, title) }
        .distinctBy { it.stream }.take(4)
    for (candidate in cached) {
        if (probeAlbumStream(candidate.stream)) {
            AppDiagnostics.info(
                "Album", "reusing playable catalog track source=" +
                    candidate.id.substringBefore(":").take(22)
            )
            return@withTimeoutOrNull candidate
        }
    }

    // No reusable link. Ask both native playback catalogs and inspect the
    // returned streams. Never use the iTunes or Deezer preview as audio.
    val q = (artist.trim() + " " + title.trim()).trim()
    val results = coroutineScope {
        val muzofond = async(Dispatchers.IO) {
            try { muzofondTracks(q) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                AppDiagnostics.failure("Album", "Muzofond refresh", e)
                emptyList()
            }
        }
        val mail = async(Dispatchers.IO) {
            try { mailRuTracks(q) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                AppDiagnostics.failure("Album", "Mail.ru refresh", e)
                emptyList()
            }
        }
        muzofond.await() + mail.await()
    }
    for (candidate in results.asSequence()
        .filter { albumSongMatches(it, artist, title) }
        .distinctBy { it.stream }
        .take(6)) {
        if (probeAlbumStream(candidate.stream)) {
            AppDiagnostics.info(
                "Album", "refreshed playable track source=" +
                    candidate.id.substringBefore(":").take(22)
            )
            return@withTimeoutOrNull candidate
        }
    }
    AppDiagnostics.warning("Album", "no accessible matching audio stream for selection")
    null
}

private fun albumKey(value: String): String = value.lowercase(Locale.ROOT)
    .replace(Regex("[^\\p{L}\\p{N}]+"), " ").trim()
    .replace(Regex("\\s+"), " ")

private fun albumSongMatches(track: Track, artist: String, title: String): Boolean {
    if (albumKey(track.title) != albumKey(title)) return false
    val singer = albumKey(track.artist)
    val selected = albumKey(artist)
    if (singer.isBlank() || selected.isBlank()) return false
    if (singer == selected) return true
    val variants = artist.split(",", "&", ";", "/").map(::albumKey)
    return variants.any { it.length >= 3 &&
        (singer == it || singer.contains(" " + it + " ")) }
}

private suspend fun probeAlbumStream(uri: String): Boolean = withContext(Dispatchers.IO) {
    val source = runCatching { URL(uri) }.getOrNull() ?: return@withContext false
    if (source.protocol != "https" && source.protocol != "http") return@withContext false
    val conn = (source.openConnection() as? HttpURLConnection) ?: return@withContext false
    try {
        conn.connectTimeout = 4_000
        conn.readTimeout = 4_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Range", "bytes=0-1")
        conn.setRequestProperty("Accept", "audio/*,application/octet-stream,*/*")
        conn.setRequestProperty(
            "User-Agent",
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 Chrome/130.0.0.0 Mobile Safari/537.36"
        )
        val host = source.host.lowercase(Locale.ROOT)
        if (host == "muzofond.fm" || host.endsWith(".muzofond.fm"))
            conn.setRequestProperty("Referer", "https://muzofond.fm/")
        else if (host == "moosic.my.mail.ru" || host.endsWith(".moosic.my.mail.ru"))
            conn.setRequestProperty("Referer", "https://my.mail.ru/music/")
        val status = conn.responseCode
        val kind = conn.contentType.orEmpty().lowercase(Locale.ROOT)
        if (status != 200 && status != 206) return@withContext false
        if ("text/html" in kind || "application/json" in kind) return@withContext false
        conn.inputStream.use { it.read() >= 0 }
    } catch (e: Exception) {
        false
    } finally {
        conn.disconnect()
    }
}
