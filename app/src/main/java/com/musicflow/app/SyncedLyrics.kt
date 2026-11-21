package com.musicflow.app

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

internal data class TimedLyric(val timeMs: Long, val line: String)
internal data class TrackLyrics(val synced: List<TimedLyric>, val plain: String?)

internal object LyricsCatalog {
    private data class CacheEntry(val at: Long, val data: TrackLyrics?)
    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private const val TTL_MS = 6L * 60L * 60L * 1000L
    private val stamp = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")

    internal fun parseLrc(value: String): List<TimedLyric> {
        val result = mutableListOf<TimedLyric>()
        for (sourceLine in value.lineSequence().take(600)) {
            val matches = stamp.findAll(sourceLine).toList()
            if (matches.isEmpty()) continue
            val body = stamp.replace(sourceLine, "").trim()
            if (body.isEmpty()) continue
            for (m in matches) {
                val minute = m.groupValues[1].toLongOrNull() ?: continue
                val second = m.groupValues[2].toLongOrNull() ?: continue
                if (second >= 60) continue
                val decimal = m.groupValues[3]
                val ms = if (decimal.isEmpty()) 0L else decimal.padEnd(3, '0').take(3)
                    .toLongOrNull() ?: 0L
                result.add(TimedLyric((minute * 60L + second) * 1000L + ms, body))
            }
        }
        return result.sortedBy { it.timeMs }.distinctBy { it.timeMs to it.line }.take(400)
    }

    suspend fun fetch(artist: String, title: String, durationMs: Long): TrackLyrics? =
        withContext(Dispatchers.IO) {
            val name = title.trim().take(120)
            val performer = artist.trim().take(120)
            if (name.isBlank() || performer.isBlank() || performer == "Неизвестный исполнитель") {
                return@withContext null
            }
            val key = (performer + "|" + name + "|" + durationMs / 1000L).lowercase()
            cache[key]?.takeIf { System.currentTimeMillis() - it.at < TTL_MS }?.let {
                return@withContext it.data
            }
            val address = "https://lrclib.net/api/search?track_name=" +
                URLEncoder.encode(name, "UTF-8") + "&artist_name=" +
                URLEncoder.encode(performer, "UTF-8")
            val connection = (URL(address).openConnection() as HttpURLConnection).apply {
                connectTimeout = 6500
                readTimeout = 8500
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "MusicFlow/0.7.30 (https://github.com/smoc1983-web/MusicFlow-)")
            }
            val result = try {
                if (connection.responseCode !in 200..299) throw IOException("LRCLIB HTTP " + connection.responseCode)
                val json = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    reader.readText().take(500_000)
                }
                val data = JSONArray(json)
                val candidates = (0 until minOf(data.length(), 25)).mapNotNull { index ->
                    val item = data.optJSONObject(index) ?: return@mapNotNull null
                    val actualTrack = item.optString("trackName")
                    val actualArtist = item.optString("artistName")
                    if (musicKey(actualTrack) != musicKey(name) ||
                        musicKey(actualArtist) != musicKey(performer)) return@mapNotNull null
                    val length = item.optLong("duration", 0L)
                    if (durationMs > 0L && length > 0L && abs(length * 1000L - durationMs) > 5_000L)
                        return@mapNotNull null
                    val synced = parseLrc(item.optString("syncedLyrics"))
                    val plain = item.optString("plainLyrics").takeIf {
                        it.isNotBlank() && it != "null"
                    }?.take(12000)
                    if (synced.isEmpty() && plain == null) null
                    else TrackLyrics(synced, plain)
                }
                candidates.firstOrNull { it.synced.isNotEmpty() } ?: candidates.firstOrNull()
            } finally { connection.disconnect() }
            cache[key] = CacheEntry(System.currentTimeMillis(), result)
            result
        }
}

@Composable
internal fun SyncedLyricsScreen(
    artist: String,
    title: String,
    durationMs: Long,
    positionMs: Long,
    modifier: Modifier = Modifier
) {
    val palette = LocalMusicFlowPalette.current
    val context = LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("musicflow_lyrics_timing_v1", Context.MODE_PRIVATE)
    }
    var offsetMs by remember {
        mutableIntStateOf(LyricsTiming.clamp(preferences.getInt("offset_ms", 0)))
    }
    fun changeOffset(newValue: Int) {
        offsetMs = LyricsTiming.clamp(newValue)
        preferences.edit().putInt("offset_ms", offsetMs).apply()
    }
    var lyrics by remember(artist, title, durationMs) { mutableStateOf<TrackLyrics?>(null) }
    var loading by remember(artist, title, durationMs) { mutableStateOf(true) }
    var error by remember(artist, title, durationMs) { mutableStateOf<String?>(null) }
    val state = rememberLazyListState()

    LaunchedEffect(artist, title, durationMs) {
        loading = true
        error = null
        try {
            lyrics = LyricsCatalog.fetch(artist, title, durationMs)
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            AppDiagnostics.failure("Lyrics", "search", failure)
            error = "Текст песни временно недоступен."
        } finally { loading = false }
    }

    val lines = lyrics?.synced.orEmpty()
    val active = LyricsTiming.activeIndex(lines.map { it.timeMs }, positionMs, offsetMs)
    LaunchedEffect(active, lines.size) {
        if (active >= 0) state.animateScrollToItem((active - 2).coerceAtLeast(0))
    }

    Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Текст песни · LRCLIB", color = palette.muted,
            style = MaterialTheme.typography.labelMedium)
        if (lines.isNotEmpty()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { changeOffset(offsetMs - 250) },
                    enabled = offsetMs > -5000) { Text("−") }
                val offsetLabel = (if (offsetMs > 0) "+" else "") +
                    "%.2f с".format(java.util.Locale.ROOT, offsetMs / 1000f)
                Text(offsetLabel, modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge)
                TextButton(onClick = { changeOffset(offsetMs + 250) },
                    enabled = offsetMs < 5000) { Text("+") }
                TextButton(onClick = { changeOffset(0) },
                    enabled = offsetMs != 0) { Text("Сброс") }
            }
            Text("«+» — текст раньше, «−» — позже · шаг 0,25 с",
                style = MaterialTheme.typography.labelSmall, color = palette.muted)
        }
        when {
            loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
            error != null -> Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            lines.isNotEmpty() -> LazyColumn(
                state = state,
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                itemsIndexed(lines) { index, item ->
                    val selected = index == active
                    Text(
                        item.line,
                        modifier = Modifier.fillMaxWidth()
                            .background(
                                if (selected) palette.accent.copy(alpha = 0.15f)
                                else palette.surface.copy(alpha = 0.2f),
                                RoundedCornerShape(8.dp)
                            )
                            .padding(horizontal = 9.dp, vertical = 8.dp),
                        color = if (selected) palette.accent else palette.foreground,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
            lyrics?.plain != null -> {
                Text("Доступен только обычный текст, без синхронизации.",
                    color = palette.muted, style = MaterialTheme.typography.bodySmall)
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item { Text(lyrics?.plain.orEmpty(), color = palette.foreground) }
                }
            }
            else -> Text("Подходящий текст не найден. Не все композиции есть в LRCLIB.",
                color = palette.muted)
        }
    }
}
