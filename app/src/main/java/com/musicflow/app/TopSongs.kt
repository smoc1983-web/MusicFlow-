package com.musicflow.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.util.Locale

internal enum class TopSongsPeriod(val label: String, val apiRange: String) {
    WEEK("Неделя", "this_week"),
    MONTH("Месяц", "this_month"),
    YEAR("Год", "this_year")
}

internal data class TopSong(
    val title: String,
    val artist: String,
    val listenCount: Long
)

/**
 * Sitewide ListenBrainz statistics, not all streaming services or a Russia chart.
 * Metadata alone cannot be played; we resolve music via existing MusicFlow sources.
 */
internal object TopSongsDirectory {
    private const val API = "https://api.listenbrainz.org/1/stats/sitewide/recordings"

    suspend fun fetch(period: TopSongsPeriod): List<TopSong> = withContext(Dispatchers.IO) {
        val connection = (URL("$API?range=${period.apiRange}&count=50").openConnection()
            as HttpURLConnection).apply {
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "MusicFlow-Android/0.7.35")
        }
        try {
            if (connection.responseCode == HttpURLConnection.HTTP_NO_CONTENT) {
                return@withContext emptyList()
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IOException("Сервис чарта ответил: HTTP ${connection.responseCode}")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val payload = JSONObject(body).optJSONObject("payload")
                ?: throw IOException("Чарт вернул неожиданный ответ")
            val recordings = payload.optJSONArray("recordings")
                ?: throw IOException("В ответе отсутствуют записи")
            (0 until recordings.length()).mapNotNull { index ->
                val record = recordings.optJSONObject(index) ?: return@mapNotNull null
                val title = record.optString("track_name").trim()
                val artist = record.optString("artist_name").trim()
                if (title.isEmpty() || artist.isEmpty()) return@mapNotNull null
                TopSong(
                    title = title.take(240),
                    artist = artist.take(200),
                    listenCount = record.optLong("listen_count", 0L).coerceAtLeast(0L)
                )
            }
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
internal fun TopSongsScreen(
    onPlay: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMusicFlowPalette.current
    val scope = rememberCoroutineScope()
    val counts = remember { NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU")) }
    val cache = remember { mutableStateMapOf<TopSongsPeriod, List<TopSong>>() }
    var period by remember { mutableStateOf(TopSongsPeriod.WEEK) }
    var refresh by remember { mutableIntStateOf(0) }
    var songs by remember { mutableStateOf<List<TopSong>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingSong by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(period, refresh) {
        error = null
        if (cache.containsKey(period)) {
            songs = cache[period].orEmpty()
            return@LaunchedEffect
        }
        loading = true
        songs = emptyList()
        try {
            val loaded = TopSongsDirectory.fetch(period)
            cache[period] = loaded
            songs = loaded
        } catch (cancel: CancellationException) {
            throw cancel
        } catch (failure: Exception) {
            AppDiagnostics.failure("Charts", "top recordings ${period.apiRange}", failure)
            error = "Не удалось загрузить чарт. Проверьте подключение и повторите."
        } finally {
            loading = false
        }
    }

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("ТОП песен", color = palette.foreground,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Мировой рейтинг · ListenBrainz", color = palette.muted,
            style = MaterialTheme.typography.bodySmall)
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TopSongsPeriod.entries.forEach { option ->
                FilterChip(selected = period == option, onClick = { period = option },
                    label = { Text(option.label) })
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {
                cache.remove(period)
                refresh++
            }, enabled = !loading) {
                Icon(Icons.Filled.Refresh, contentDescription = "Обновить чарт")
            }
        }
        Text("Показаны прослушивания, зарегистрированные в ListenBrainz. " +
            "Это не статистика всех музыкальных сервисов или только России.",
            color = palette.muted, style = MaterialTheme.typography.labelSmall)
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let {
            Text(it, color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
        if (!loading && error == null && songs.isEmpty()) {
            Text("За выбранный период пока нет данных.", color = palette.muted)
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(7.dp)) {
            itemsIndexed(songs, key = { index, _ -> "${period.apiRange}:$index" }) { index, song ->
                val key = "${period.apiRange}:$index"
                val resolving = pendingSong == key
                Surface(color = palette.surface, shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().clickable(enabled = pendingSong == null) {
                            pendingSong = key
                            error = null
                            scope.launch {
                                try {
                                    val tracks = findRecording(song.artist, song.title)
                                    if (tracks.isNotEmpty()) onPlay(tracks.first())
                                    else error = "Не найден доступный поток: ${song.artist} — ${song.title}"
                                } catch (cancel: CancellationException) {
                                    throw cancel
                                } catch (failure: Exception) {
                                    AppDiagnostics.failure("Charts", "play chart selection", failure)
                                    error = "Не удалось найти аудиозапись. Повторите попытку."
                                } finally {
                                    pendingSong = null
                                }
                            }
                        }.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${index + 1}", modifier = Modifier.width(34.dp),
                            color = palette.accent, fontWeight = FontWeight.Bold)
                        Icon(Icons.Filled.MusicNote, contentDescription = null,
                            tint = palette.accent, modifier = Modifier.size(35.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = palette.foreground, fontWeight = FontWeight.Medium)
                            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                color = palette.muted, style = MaterialTheme.typography.bodySmall)
                            Text("${counts.format(song.listenCount)} прослушиваний",
                                color = palette.muted, style = MaterialTheme.typography.labelSmall)
                        }
                        Spacer(Modifier.width(8.dp))
                        if (resolving) CircularProgressIndicator(Modifier.size(24.dp),
                            strokeWidth = 2.dp)
                        else Icon(Icons.Filled.PlayArrow, contentDescription = "Найти и включить",
                            tint = palette.accent)
                    }
                }
            }
        }
    }
}
