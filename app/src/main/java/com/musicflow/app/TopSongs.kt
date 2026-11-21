package com.musicflow.app

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.rememberAsyncImagePainter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

internal enum class TopSongsPeriod(val label: String, val apiRange: String) {
    WEEK("Неделя", "this_week"),
    MONTH("Месяц", "this_month"),
    YEAR("Год", "this_year")
}
internal enum class TopSongsRegion(val label: String) {
    RUSSIA("Россия"), WORLD("Мир")
}
internal data class TopSong(
    val title: String,
    val artist: String,
    val listenCount: Long,
    val artwork: String? = null
)

/** Russia: sum of daily listeners for charted days, not monthly unique users or plays.
 * Archive observations begin Oct 2026; "year" is therefore a partial YTD snapshot.
 * World: ListenBrainz recorded listens, not all commercial streaming platforms.
 */
internal object TopSongsDirectory {
    private const val WORLD_API = "https://api.listenbrainz.org/1/stats/sitewide/recordings"
    private const val RUSSIA_HOST = "https://blog.upsound.ai"

    // Period paths: 7d/, YYYY-MM/ and YYYY/ (Moscow calendar date).
    internal fun russiaPath(period: TopSongsPeriod, now: LocalDate): String = when (period) {
        TopSongsPeriod.WEEK -> "/charts/7d/"
        TopSongsPeriod.MONTH -> "/charts/%04d-%02d/".format(Locale.ROOT, now.year, now.monthValue)
        TopSongsPeriod.YEAR -> "/charts/" + now.year + "/"
    }

    internal fun parseRussia(html: String): List<TopSong> {
        val doc = Jsoup.parse(html, RUSSIA_HOST)
        return doc.select("ol.rows > li.row").mapNotNull { row ->
            val title = row.selectFirst("a.title")?.text()?.trim().orEmpty()
            val artist = row.selectFirst("span.artists")?.text()?.trim().orEmpty()
            val count = row.selectFirst("span.num")?.ownText()
                ?.filter { it.isDigit() }?.toLongOrNull() ?: 0L
            if (title.isBlank() || artist.isBlank() || count <= 0L) return@mapNotNull null
            val cover = row.selectFirst("div.cover img")?.absUrl("src")
                ?.takeIf { it.startsWith("https://blog.upsound.ai/") }
            TopSong(title.take(240), artist.take(200), count, cover)
        }.distinctBy { musicKey(it.artist) + "::" + musicKey(it.title) }.take(100)
    }

    suspend fun fetch(period: TopSongsPeriod, region: TopSongsRegion): List<TopSong> =
        when (region) {
            TopSongsRegion.RUSSIA -> withContext(Dispatchers.IO) {
                val now = LocalDate.now(ZoneId.of("Europe/Moscow"))
                val url = RUSSIA_HOST + russiaPath(period, now)
                val document = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Linux; Android 14; Mobile) MusicFlow/0.7.36")
                    .header("Accept-Language", "ru-RU,ru;q=0.9")
                    .timeout(16_000)
                    .maxBodySize(1_500_000)
                    .get()
                val songs = parseRussia(document.outerHtml())
                if (songs.isEmpty()) throw IOException("Нет строк российского чарта: " + url)
                songs
            }
            TopSongsRegion.WORLD -> fetchWorld(period)
        }

    private suspend fun fetchWorld(period: TopSongsPeriod): List<TopSong> =
        withContext(Dispatchers.IO) {
            val endpoint = WORLD_API + "?range=" + period.apiRange + "&count=100"
            val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "MusicFlow-Android/0.7.36")
            }
            try {
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("ListenBrainz HTTP " + connection.responseCode)
                }
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val recordings = JSONObject(body).optJSONObject("payload")
                    ?.optJSONArray("recordings")
                    ?: throw IOException("Ответ ListenBrainz не содержит список песен")
                (0 until recordings.length()).mapNotNull { index ->
                    val item = recordings.optJSONObject(index) ?: return@mapNotNull null
                    val title = item.optString("track_name").trim()
                    val artist = item.optString("artist_name").trim()
                    val count = item.optLong("listen_count", 0L)
                    if (title.isBlank() || artist.isBlank()) return@mapNotNull null
                    TopSong(title.take(240), artist.take(200), count.coerceAtLeast(0L))
                }
            } finally {
                connection.disconnect()
            }
        }
}

@Composable
internal fun TopSongHomeRow(
    song: TopSong,
    index: Int,
    isPending: Boolean,
    enabled: Boolean,
    isRussia: Boolean,
    onPlay: () -> Unit
) {
    val palette = LocalMusicFlowPalette.current
    val numberFormat = NumberFormat.getIntegerInstance(Locale.forLanguageTag("ru-RU"))
    Surface(
        color = palette.surface, shape = RoundedCornerShape(13.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onPlay)
                .padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text((index + 1).toString(), modifier = Modifier.width(30.dp),
                color = palette.accent, fontWeight = FontWeight.Bold)
            if (!song.artwork.isNullOrBlank()) {
                Image(rememberAsyncImagePainter(song.artwork), contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(46.dp).clip(RoundedCornerShape(9.dp)))
            } else {
                Icon(Icons.Filled.MusicNote, contentDescription = null,
                    tint = palette.accent, modifier = Modifier.size(42.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = palette.foreground, fontWeight = FontWeight.Medium)
                Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = palette.muted, style = MaterialTheme.typography.bodySmall)
                Text(
                    numberFormat.format(song.listenCount) + if (isRussia)
                        " · сумма дневных слушателей" else " · прослушиваний",
                    color = palette.muted, style = MaterialTheme.typography.labelSmall,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(5.dp))
            if (isPending) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
            else Icon(Icons.Filled.PlayArrow, contentDescription = "Найти и воспроизвести",
                tint = palette.accent)
        }
    }
}
