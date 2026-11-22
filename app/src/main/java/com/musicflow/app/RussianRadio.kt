package com.musicflow.app

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

internal data class RussianRadioStation(
    val uuid: String,
    val name: String,
    val stream: String,
    val favicon: String?,
    val tags: String,
    val codec: String,
    val bitrate: Int
) {
    val mediaId: String get() = "radio:$uuid"
    fun toTrack(): Track = Track(
        id = mediaId,
        title = name,
        artist = "Русское радио · прямой эфир",
        artwork = favicon,
        stream = stream,
        durationSec = 0
    )
}

internal object RussianRadioDirectory {
    // Prefer the active round-robin DNS pool; retired mirrors may not resolve.
    private val servers = listOf(
        "https://all.api.radio-browser.info",
        "https://de1.api.radio-browser.info"
    )

    // Do not resolve localhost, loopback, intranet or private address URLs as
    // radio stations. Streams must be public HTTP(S) radio endpoints.
    internal fun isPublicStream(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        if (uri.scheme !in listOf("https", "http")) return false
        val host = uri.host?.lowercase()?.removeSuffix(".") ?: return false
        if (host.isBlank() || host == "localhost" || host.endsWith(".local") ||
            host.endsWith(".internal") || host.contains("@") ||
            host.startsWith("127.") || host.startsWith("10.") ||
            host.startsWith("192.168.") || host.startsWith("169.254.") ||
            host.startsWith("0.") || host == "::1" || host.startsWith("fc") &&
                host.contains(':') || host.startsWith("fd") && host.contains(':')) return false
        if (host.matches(Regex("""172\.\d{1,3}\..*"""))) {
            val block = host.split('.').getOrNull(1)?.toIntOrNull() ?: return false
            if (block in 16..31) return false
        }
        return true
    }

    /**
     * Do not discard stations only because the directory's remote health checker
     * cannot reach them. Russian streams may work from the listener's network
     * even when the public Radio Browser mirror marks them as unavailable.
     *
     * A successful HTTP response with no usable stations is not a successful
     * catalog: try another mirror and then the alternate directory query.
     */
    suspend fun stations(query: String = ""): List<RussianRadioStation> =
        withContext(Dispatchers.IO) {
            val term = query.trim().take(80)
            val encoded = URLEncoder.encode(term, "UTF-8")
            val basePaths = if (term.isBlank()) listOf(
                "/json/stations/bycountrycodeexact/RU?hidebroken=false&order=clickcount&reverse=true&limit=350",
                "/json/stations/search?countrycode=RU&hidebroken=false&order=clickcount&reverse=true&limit=350"
            ) else listOf(
                "/json/stations/search?countrycode=RU&name=$encoded&hidebroken=false&order=clickcount&reverse=true&limit=200",
                "/json/stations/byname/$encoded?hidebroken=false&order=clickcount&reverse=true&limit=200"
            )
            var failure: Throwable? = null
            for (base in basePaths) {
                for (server in servers) {
                    try {
                        val connection = (URL(server + base).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 5_500
                            readTimeout = 8_500
                            setRequestProperty("User-Agent", "MusicFlow/0.7.39 (https://github.com/smoc1983-web/MusicFlow-)")
                            setRequestProperty("Accept", "application/json")
                            setRequestProperty("Accept-Language", "ru")
                        }
                        val payload = try {
                            if (connection.responseCode !in 200..299) {
                                throw IOException("Radio Browser HTTP " + connection.responseCode)
                            }
                            connection.inputStream.bufferedReader(Charsets.UTF_8).use { input ->
                                val output = StringBuilder()
                                val buffer = CharArray(8192)
                                while (output.length < 2_000_000) {
                                    val amount = input.read(buffer, 0, minOf(buffer.size, 2_000_000 - output.length))
                                    if (amount < 0) break
                                    if (amount > 0) output.append(buffer, 0, amount)
                                }
                                output.toString()
                            }
                        } finally {
                            connection.disconnect()
                        }
                        val arr = JSONArray(payload)
                        val found = (0 until arr.length()).mapNotNull { index ->
                            val item = arr.optJSONObject(index) ?: return@mapNotNull null
                            val uuid = item.optString("stationuuid").trim()
                            val name = item.optString("name").trim().take(130)
                            if (!isListingCandidate(item.optString("countrycode"), uuid, name)) {
                                return@mapNotNull null
                            }
                            // The directory's lastcheckok status is deliberately not
                            // used here: it does not prove regional reachability.
                            val stream = item.optString("url_resolved")
                                .takeIf(::isPublicStream)
                                ?: item.optString("url").takeIf(::isPublicStream)
                                ?: return@mapNotNull null
                            RussianRadioStation(
                                uuid = uuid,
                                name = name,
                                stream = stream,
                                favicon = item.optString("favicon").takeIf { it.startsWith("https://") },
                                tags = item.optString("tags").take(140),
                                codec = item.optString("codec").take(16),
                                bitrate = item.optInt("bitrate", 0)
                            )
                        }.distinctBy { it.uuid }
                        if (found.isNotEmpty()) {
                            AppDiagnostics.info("Radio", "catalog mirror returned " + found.size + " stations")
                            return@withContext found
                        }
                        failure = IOException("Каталог Radio Browser пуст: " + server)
                        AppDiagnostics.info("Radio", "empty catalog mirror " + server)
                    } catch (cancel: CancellationException) {
                        throw cancel
                    } catch (error: Exception) {
                        failure = error
                        AppDiagnostics.failure("Radio", "catalog mirror request", error)
                    }
                }
            }
            throw IOException("Не удалось получить список радиостанций. Проверьте интернет.", failure)
        }

    internal fun isListingCandidate(countryCode: String, uuid: String, name: String): Boolean =
        countryCode.equals("RU", ignoreCase = true) && uuid.isNotBlank() && name.isNotBlank()

    private const val CACHE_PREFS = "musicflow_radio_catalog_cache"
    private const val CACHE_KEY = "last_good_stations"

    /** Last good popular catalog. Never overwrites or clears radio favorites. */
    fun cachedStations(context: Context): List<RussianRadioStation> = runCatching {
        val raw = context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            .getString(CACHE_KEY, "[]")
        val json = JSONArray(raw)
        (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val uuid = item.optString("uuid")
            val name = item.optString("name")
            val stream = item.optString("stream")
            if (uuid.isBlank() || name.isBlank() || !isPublicStream(stream)) null
            else RussianRadioStation(
                uuid, name, stream,
                item.optString("favicon").takeIf { it.startsWith("https://") },
                item.optString("tags"), item.optString("codec"), item.optInt("bitrate")
            )
        }
    }.getOrDefault(emptyList())

    fun saveCachedStations(context: Context, stations: List<RussianRadioStation>) {
        if (stations.isEmpty()) return
        val json = JSONArray()
        stations.take(250).forEach { station ->
            json.put(JSONObject().apply {
                put("uuid", station.uuid)
                put("name", station.name)
                put("stream", station.stream)
                put("favicon", station.favicon ?: "")
                put("tags", station.tags)
                put("codec", station.codec)
                put("bitrate", station.bitrate)
            })
        }
        context.getSharedPreferences(CACHE_PREFS, Context.MODE_PRIVATE)
            .edit().putString(CACHE_KEY, json.toString()).apply()
    }

    private const val PREFS = "musicflow_radio_favorites"
    private const val KEY = "stations"

    fun favorites(context: Context): List<RussianRadioStation> = runCatching {
        val json = JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"))
        (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            val uuid = item.optString("uuid")
            val stream = item.optString("stream")
            if (uuid.isBlank() || !isPublicStream(stream)) null else RussianRadioStation(
                uuid, item.optString("name"), stream,
                item.optString("favicon").takeIf { it.startsWith("https://") },
                item.optString("tags"), item.optString("codec"), item.optInt("bitrate")
            )
        }
    }.getOrDefault(emptyList())

    fun saveFavorites(context: Context, stations: List<RussianRadioStation>) {
        val json = JSONArray()
        stations.take(100).forEach { station ->
            json.put(JSONObject().apply {
                put("uuid", station.uuid)
                put("name", station.name)
                put("stream", station.stream)
                put("favicon", station.favicon ?: "")
                put("tags", station.tags)
                put("codec", station.codec)
                put("bitrate", station.bitrate)
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, json.toString()).apply()
    }
}

@Composable
internal fun RussianRadioScreen(
    activeId: String,
    playing: Boolean,
    onPlay: (RussianRadioStation) -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    val palette = LocalMusicFlowPalette.current
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var applied by remember { mutableStateOf("") }
    var refreshIndex by remember { mutableIntStateOf(0) }
    var favoritesOnly by remember { mutableStateOf(false) }
    var favorites by remember { mutableStateOf(RussianRadioDirectory.favorites(context)) }
    // Display an immediately usable station list even with no working DNS.
    var stations by remember {
        mutableStateOf(RussianRadioDirectory.cachedStations(context).ifEmpty {
            RussianRadioFallback.bundledStations(context)
        })
    }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(applied, refreshIndex) {
        loading = true
        error = null
        if (applied.isNotBlank()) stations = emptyList()
        try {
            val fresh = RussianRadioDirectory.stations(applied)
            stations = fresh
            if (applied.isBlank()) RussianRadioDirectory.saveCachedStations(context, fresh)
        } catch (failure: Exception) {
            if (failure is CancellationException) throw failure
            // The fallback uses a different hostname and, if necessary,
            // the APK-bundled Russian station list without any network.
            val fallback = RussianRadioFallback.stations(context, applied)
            if (fallback.isNotEmpty()) {
                stations = fallback
                if (applied.isBlank()) RussianRadioDirectory.saveCachedStations(context, fallback)
                error = "Основной каталог недоступен. Показаны станции из резервного списка."
            } else {
                error = failure.message ?: "Не удалось получить станции"
            }
        } finally {
            loading = false
        }
    }

    val shown = if (favoritesOnly) favorites else stations
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Русское радио", color = palette.foreground,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Радиостанции России · прямой эфир, не поиск отдельных песен",
            color = palette.muted, style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it.take(80) },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Название станции") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { applied = query.trim() })
        )
        Row(verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !favoritesOnly, onClick = { favoritesOnly = false },
                label = { Text("Популярные") })
            FilterChip(selected = favoritesOnly, onClick = { favoritesOnly = true },
                label = { Text("Избранное") })
            IconButton(onClick = {
                // A refresh must re-run LaunchedEffect even for the same query.
                applied = query.trim()
                refreshIndex++
            }) { Icon(Icons.Filled.Refresh, "Обновить станции") }
        }
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (shown.isEmpty() && !loading) {
            Text(if (favoritesOnly) "Нет избранных станций" else
                "Станции не найдены. Измените запрос.", color = palette.muted)
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            items(shown, key = { it.uuid }) { station ->
                val active = station.mediaId == activeId
                Surface(
                    color = palette.surface,
                    shape = RoundedCornerShape(13.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { if (active && playing) onStop() else onPlay(station) }
                            .padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (station.favicon != null) {
                            Image(rememberAsyncImagePainter(station.favicon),
                                contentDescription = null,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier.size(42.dp).clip(RoundedCornerShape(8.dp)))
                        } else {
                            Icon(Icons.Filled.Radio, contentDescription = null,
                                tint = palette.accent, modifier = Modifier.size(42.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(station.name, maxLines = 1, overflow = TextOverflow.Ellipsis,
                                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium)
                            val details = listOf(station.codec, station.bitrate.takeIf { it > 0 }?.let { "$it кбит/с" })
                                .filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
                            Text(if (active) (if (playing) "● Сейчас в эфире" else "На паузе") else details,
                                color = if (active) palette.accent else palette.muted,
                                maxLines = 1, style = MaterialTheme.typography.labelSmall)
                        }
                        Icon(if (active && playing) androidx.compose.material.icons.Icons.Filled.Radio
                            else Icons.Filled.PlayArrow, "Слушать", tint = palette.accent)
                        val saved = favorites.any { it.uuid == station.uuid }
                        IconButton(onClick = {
                            favorites = if (saved) favorites.filterNot { it.uuid == station.uuid }
                                else (listOf(station) + favorites).distinctBy { it.uuid }.take(100)
                            RussianRadioDirectory.saveFavorites(context, favorites)
                        }) {
                            Icon(if (saved) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                contentDescription = if (saved) "Убрать из избранного" else "В избранное",
                                tint = if (saved) palette.accent else palette.muted)
                        }
                    }
                }
            }
        }
    }
}
