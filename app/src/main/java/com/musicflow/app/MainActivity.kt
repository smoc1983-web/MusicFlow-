package com.musicflow.app

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.compose.rememberAsyncImagePainter
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private val Violet = Color(0xFFAC80FF)
private val Panel = Color(0xFF201B2C)

private data class Track(
    val id: String,
    val title: String,
    val artist: String,
    val artwork: String?,
    val stream: String,
    val durationSec: Long
)

class MainActivity : ComponentActivity() {
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private val playerState = mutableStateOf<MediaController?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(this, token).buildAsync().also { future ->
            future.addListener({
                runCatching { playerState.value = future.get() }
            }, ContextCompat.getMainExecutor(this))
        }
        setContent {
            val scheme = darkColorScheme(
                primary = Violet,
                background = Color(0xFF110F18),
                surface = Panel,
                onBackground = Color.White,
                onSurface = Color.White
            )
            MaterialTheme(colorScheme = scheme) {
                Surface(color = scheme.background) {
                MusicScreen(
                    player = playerState.value,
                    saved = { loadFavorites() },
                    save = { saveFavorites(it) }
                )
                }
            }
        }
    }

    override fun onDestroy() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onDestroy()
    }

    private fun loadFavorites(): List<Track> = runCatching {
        val text = getSharedPreferences("musicflow", MODE_PRIVATE).getString("favorites", "[]")
        val array = JSONArray(text)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val id = obj.optString("id")
            val stream = obj.optString("stream")
            if (id.isBlank() || !stream.startsWith("https://")) null
            else Track(
                id, obj.optString("title"), obj.optString("artist"),
                obj.optString("artwork").takeIf { it.startsWith("https://") },
                stream, obj.optLong("durationSec")
            )
        }
    }.getOrDefault(emptyList())

    private fun saveFavorites(items: List<Track>) {
        val a = JSONArray()
        items.forEach { t ->
            a.put(JSONObject().apply {
                put("id", t.id)
                put("title", t.title)
                put("artist", t.artist)
                put("artwork", t.artwork ?: "")
                put("stream", t.stream)
                put("durationSec", t.durationSec)
            })
        }
        getSharedPreferences("musicflow", MODE_PRIVATE)
            .edit().putString("favorites", a.toString()).apply()
    }
}

@Composable
private fun MusicScreen(
    player: MediaController?,
    saved: () -> List<Track>,
    save: (List<Track>) -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val favorites = remember { mutableStateListOf<Track>().also { it.addAll(saved()) } }
    var tracks by remember { mutableStateOf<List<Track>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var tab by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(true) }
    var problem by remember { mutableStateOf<String?>(null) }
    var playing by remember { mutableStateOf(false) }
    var trackTitle by remember { mutableStateOf("") }
    var trackArtist by remember { mutableStateOf("") }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }

    fun fetch(query: String = "") {
        scope.launch {
            busy = true
            problem = null
            try {
                tracks = audiusTracks(query)
            } catch (e: Exception) {
                problem = "Не удалось загрузить каталог. " + (e.message ?: "Проверьте интернет и DNS")
            } finally {
                busy = false
            }
        }
    }
    LaunchedEffect(Unit) { fetch() }
    LaunchedEffect(player) {
        while (player != null) {
            playing = player.isPlaying
            trackTitle = player.currentMediaItem?.mediaMetadata?.title?.toString() ?: ""
            trackArtist = player.currentMediaItem?.mediaMetadata?.artist?.toString() ?: ""
            positionMs = player.currentPosition.coerceAtLeast(0)
            durationMs = player.duration.coerceAtLeast(0)
            delay(650)
        }
    }

    fun queueAndPlay(list: List<Track>, index: Int) {
        val p = player ?: run {
            problem = "Плеер подключается. Повторите попытку."
            return
        }
        p.setMediaItems(list.map { t ->
            MediaItem.Builder()
                .setMediaId(t.id)
                .setUri(t.stream)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(t.title)
                        .setArtist(t.artist)
                        .setArtworkUri(t.artwork?.let(Uri::parse))
                        .build()
                ).build()
        }, index, 0L)
        p.prepare()
        p.play()
    }

    val visible = if (tab == 2) favorites.toList() else tracks
    Column(
        Modifier.fillMaxSize().background(Color(0xFF110F18))
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(20.dp))
        Text("MusicFlow", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text("Музыка онлайн · Audius", color = Color.LightGray)
        Spacer(Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Главная", "Поиск", "Избранное").forEachIndexed { index, title ->
                FilterChip(
                    selected = tab == index,
                    onClick = { tab = index },
                    label = { Text(title) }
                )
            }
        }
        if (tab == 1) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                label = { Text("Исполнитель или название") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Button(
                onClick = { fetch(search) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Найти музыку") }
        }
        Spacer(Modifier.height(12.dp))

        if (busy && tab != 2) LinearProgressIndicator(Modifier.fillMaxWidth())
        problem?.let {
            Text(it, color = Color(0xFFFFB3A8), modifier = Modifier.padding(vertical = 10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { fetch(if (tab == 1) search else "") }) {
                    Text("Повторить")
                }
                TextButton(onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Intent.ACTION_VIEW,
                                Uri.parse("https://api.audius.co/v1/tracks/trending?app_name=MusicFlowAndroid&limit=2")
                            )
                        )
                    }
                }) {
                    Text("Проверить API")
                }
            }
        }
        Text(
            if (tab == 2) "Сохранённые треки" else if (tab == 1) "Результаты поиска" else "Популярное сейчас",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(visible, key = { it.id }) { track ->
                val isFavorite = favorites.any { it.id == track.id }
                Row(
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
                        .background(Panel).clickable {
                            val index = visible.indexOf(track)
                            if (index >= 0) queueAndPlay(visible, index)
                        }.padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painter = rememberAsyncImagePainter(track.artwork),
                        contentDescription = "Обложка",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(56.dp).clip(RoundedCornerShape(9.dp))
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(track.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                        Text(track.artist, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Color.LightGray)
                    }
                    TextButton(onClick = {
                        if (isFavorite) favorites.removeAll { it.id == track.id }
                        else favorites.add(0, track)
                        save(favorites.toList())
                    }) { Text(if (isFavorite) "♥" else "♡") }
                }
            }
            if (visible.isEmpty() && !busy) item {
                Text(
                    if (tab == 2) "Избранного пока нет" else "Треки не найдены",
                    color = Color.LightGray, modifier = Modifier.padding(12.dp)
                )
            }
        }
        if (trackTitle.isNotBlank()) {
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(Panel).padding(14.dp)
            ) {
                Text(trackTitle, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(trackArtist, color = Color.LightGray, maxLines = 1)
                val end = durationMs.coerceAtLeast(1L)
                Slider(
                    value = positionMs.coerceIn(0L, end).toFloat(),
                    onValueChange = { player?.seekTo(it.toLong()) },
                    valueRange = 0f..end.toFloat(),
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { player?.seekToPreviousMediaItem() }) { Text("⏮") }
                    Button(onClick = { if (playing) player?.pause() else player?.play() }) {
                        Text(if (playing) "Пауза" else "Слушать")
                    }
                    TextButton(onClick = { player?.seekToNextMediaItem() }) { Text("⏭") }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

private suspend fun audiusTracks(query: String): List<Track> = withContext(Dispatchers.IO) {
    val hosts = listOf("https://api.audius.co/v1", "https://discoveryprovider.audius.co/v1")
    val diagnostics = mutableListOf<String>()
    for (host in hosts) {
        try {
            val url = Uri.parse(host + if (query.isBlank()) "/tracks/trending" else "/tracks/search")
                .buildUpon().appendQueryParameter("app_name", "MusicFlowAndroid")
                .appendQueryParameter("limit", "40").apply {
                    if (query.isNotBlank()) appendQueryParameter("query", query.trim())
                }.build().toString()
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 15_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
            }
            val payload = try {
                if (conn.responseCode !in 200..299) {
                    val snippet = conn.errorStream?.bufferedReader()?.use { it.readText().take(100) } ?: ""
                    error("HTTP " + conn.responseCode + (if (snippet.isBlank()) "" else ": " + snippet))
                }
                conn.inputStream.bufferedReader().use { it.readText() }
            } finally {
                conn.disconnect()
            }
            val array = JSONObject(payload).optJSONArray("data")
                ?: error("Некорректный ответ каталога")
            val result = mutableListOf<Track>()
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val title = item.optString("title")
                if (id.isBlank() || title.isBlank() || !item.optBoolean("is_streamable", true)) continue
                val access = item.optJSONObject("access")
                if (access != null && !access.optBoolean("stream", true)) continue
                val artist = item.optJSONObject("user")?.optString("name")
                    ?.takeIf(String::isNotBlank) ?: "Неизвестный исполнитель"
                val art = item.optJSONObject("artwork")
                val artwork = listOf("480x480", "1000x1000", "150x150")
                    .firstNotNullOfOrNull { key ->
                        art?.optString(key)?.takeIf { it.startsWith("https://") }
                    }
                val stream = Uri.parse(host + "/tracks/" + Uri.encode(id) + "/stream")
                    .buildUpon().appendQueryParameter("app_name", "MusicFlowAndroid")
                    .build().toString()
                result.add(Track(id, title, artist, artwork, stream, item.optLong("duration")))
            }
            return@withContext result.distinctBy { it.id }
        } catch (failure: Exception) {
            val reason = when (failure) {
                is java.net.UnknownHostException -> "DNS: адрес не найден"
                is java.net.SocketTimeoutException -> "превышено время ожидания"
                is javax.net.ssl.SSLException -> "ошибка TLS/SSL"
                is java.net.ConnectException -> "соединение отклонено"
                else -> (failure.message ?: failure.javaClass.simpleName).take(150)
            }
            diagnostics.add(host.substringAfter("https://").substringBefore("/") + ": " + reason)
        }
    }
    throw IllegalStateException(diagnostics.joinToString("; ").ifBlank { "Нет ответа от Audius" })
}
