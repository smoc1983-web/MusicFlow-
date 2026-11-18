package com.musicflow.app

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

// Internet Archive's Live Music Archive contains artist-permitted concert recordings.
// We stream directly from archive.org; no offline copies are made.
internal suspend fun archiveTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
    val clean = query.trim().take(90)
        .replace(Regex("[^\\p{L}\\p{N} \\-']"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    val archiveQuery = if (clean.isBlank()) {
        "collection:etree AND mediatype:etree"
    } else {
        "collection:etree AND (title:\"$clean\" OR creator:\"$clean\")"
    }
    val searchUrl = Uri.parse("https://archive.org/advancedsearch.php").buildUpon()
        .appendQueryParameter("q", archiveQuery)
        .appendQueryParameter("fl[]", "identifier")
        .appendQueryParameter("fl[]", "title")
        .appendQueryParameter("fl[]", "creator")
        .appendQueryParameter("rows", "8")
        .appendQueryParameter("sort[]", "downloads desc")
        .appendQueryParameter("output", "json")
        .build().toString()
    val docs = JSONObject(archiveGet(searchUrl)).optJSONObject("response")?.optJSONArray("docs")
        ?: return@withContext emptyList()
    coroutineScope {
        val tasks = (0 until docs.length()).mapNotNull { index ->
            val doc = docs.optJSONObject(index) ?: return@mapNotNull null
            val identifier = doc.optString("identifier").takeIf { it.isNotBlank() }
                ?: return@mapNotNull null
            async(Dispatchers.IO) {
                runCatching { archiveItemTracks(identifier, doc) }.getOrDefault(emptyList())
            }
        }
        tasks.awaitAll().flatten().distinctBy { it.id }
    }
}

private fun archiveItemTracks(identifier: String, doc: JSONObject): List<Track> {
    val safeId = Uri.encode(identifier)
    val item = JSONObject(archiveGet("https://archive.org/metadata/" + safeId))
    if (item.optBoolean("is_dark") || item.optBoolean("is_restricted")) return emptyList()
    val files = item.optJSONArray("files") ?: return emptyList()
    val metadata = item.optJSONObject("metadata")
    val creator = (doc.optJSONArray("creator")?.optString(0)
        ?: doc.optString("creator").takeIf { it.isNotBlank() }
        ?: metadata?.optString("creator").orEmpty()).ifBlank { "Live Music Archive" }
    val concert = doc.optString("title").ifBlank { metadata?.optString("title") ?: identifier }
    val cover = "https://archive.org/services/img/" + safeId
    val result = mutableListOf<Track>()
    for (i in 0 until files.length()) {
        val file = files.optJSONObject(i) ?: continue
        val fileName = file.optString("name")
        if (!fileName.endsWith(".mp3", ignoreCase = true) && !fileName.endsWith(".ogg", ignoreCase = true)) continue
        if (file.optBoolean("private") || file.optBoolean("source_missing")) continue
        val itemTitle = file.optString("title").ifBlank {
            fileName.substringAfterLast('/').substringBeforeLast('.').take(55)
        }
        val fullTitle = if (itemTitle.contains(concert, ignoreCase = true)) itemTitle
            else itemTitle + " · " + concert
        val audioUrl = "https://archive.org/download/" + safeId + "/" + Uri.encode(fileName, "/")
        result += Track(
            id = "archive:" + identifier + ":" + fileName,
            title = fullTitle.take(140),
            artist = creator.take(90),
            artwork = cover,
            stream = audioUrl,
            durationSec = 0L
        )
        if (result.size == 2) break
    }
    return result
}

private fun archiveGet(url: String): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 8_000
        readTimeout = 12_000
        instanceFollowRedirects = true
        setRequestProperty("User-Agent", "MusicFlowPreview/0.2 (Android)")
        setRequestProperty("Accept", "application/json")
    }
    return try {
        if (connection.responseCode !in 200..299) {
            error("HTTP " + connection.responseCode)
        }
        connection.inputStream.bufferedReader().use { it.readText() }
    } finally {
        connection.disconnect()
    }
}
