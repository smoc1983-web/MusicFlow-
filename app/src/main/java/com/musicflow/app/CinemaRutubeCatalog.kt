package com.musicflow.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder

internal data class CinemaRutubeShelf(
    val section: String,
    val title: String,
    val url: String
)

internal data class CinemaRutubePage(
    val films: List<CinemaTitle>,
    val next: String?
)

/**
 * API-based Rutube shelves: real title/poster metadata, official editorial
 * film genres, and native search. No HTML guesswork or code execution.
 */
internal object CinemaRutubeCatalog {
    private const val FEED = "https://rutube.ru/api/feeds/movies/?format=json"
    private const val MAX_BYTES = 2_400_000

    fun permittedApi(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        return uri.scheme.equals("https", true) &&
            uri.host?.lowercase() in listOf("rutube.ru", "www.rutube.ru") &&
            uri.userInfo == null && uri.fragment == null &&
            uri.port in listOf(-1, 443) &&
            uri.path.orEmpty().startsWith("/api/") && url.length <= 2048
    }

    /**
     * Resolve only RUTUBE HTTPS /api/... redirects. In particular, the
     * movies feed sends 301 to the canonical trailing-slash path.
     */
    internal fun redirectTarget(current: String, location: String?): String? {
        if (location.isNullOrBlank()) return null
        val target = runCatching {
            URI(current).resolve(location.trim()).normalize().toString()
        }.getOrNull() ?: return null
        return target.takeIf(::permittedApi)
    }

    internal fun get(url: String): String {
        require(permittedApi(url)) {
            "Для каталога нужен поддерживаемый публичный API."
        }
        var current = url
        val visited = hashSetOf<String>()
        repeat(5) { hop ->
            require(visited.add(current)) {
                "Обнаружено циклическое перенаправление каталога"
            }
            val conn = URL(current).openConnection() as HttpURLConnection
            conn.connectTimeout = 7_000
            conn.readTimeout = 13_000
            conn.instanceFollowRedirects = false
            conn.setRequestProperty("User-Agent",
                "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 Chrome/130 Mobile Safari/537.36")
            conn.setRequestProperty("Referer", "https://rutube.ru/")
            conn.setRequestProperty("Accept", "application/json")
            try {
                val status = conn.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    check(hop < 4) {
                        "Каталог перенаправляет запрос слишком много раз"
                    }
                    current = redirectTarget(
                        current, conn.getHeaderField("Location")
                    ) ?: throw IllegalStateException(
                        "Сервер перенаправил каталог на недопустимый адрес"
                    )
                } else {
                    check(status == 200) { "Каталог HTTP $status" }
                    conn.inputStream.use { input ->
                        val bytes = ByteArrayOutputStream()
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n == -1) break
                            if (n > 0) {
                                check(bytes.size() + n <= MAX_BYTES) {
                                    "Слишком большой ответ киноисточника"
                                }
                                bytes.write(buffer, 0, n)
                            }
                        }
                        return bytes.toString("UTF-8")
                    }
                }
            } finally {
                conn.disconnect()
            }
        }
        error("Каталог недоступен: превышен лимит перенаправлений")
    }

    fun parseShelves(raw: String): List<CinemaRutubeShelf> {
        val tabs = JSONObject(raw).optJSONArray("tabs") ?: return emptyList()
        val items = mutableListOf<CinemaRutubeShelf>()
        val used = hashSetOf<String>()
        for (i in 0 until tabs.length()) {
            val tab = tabs.optJSONObject(i) ?: continue
            val section = tab.optString("name").trim().take(42)
            if (section !in listOf("Главная", "По жанрам", "По годам",
                    "По странам", "Оскар")) continue
            val resources = tab.optJSONArray("resources") ?: continue
            for (j in 0 until minOf(resources.length(), 45)) {
                val res = resources.optJSONObject(j) ?: continue
                val name = res.optString("name").trim().take(65)
                val address = res.optString("url")
                if (name.isBlank() || !permittedApi(address) ||
                    !used.add(section + "/" + address)) continue
                // Editorial collections can be empty, but the native UI handles that.
                items.add(CinemaRutubeShelf(section, name, address))
            }
        }
        return items
    }

    suspend fun shelves(): List<CinemaRutubeShelf> = withContext(Dispatchers.IO) {
        parseShelves(get(FEED))
    }

    private fun safeImage(raw: String): String? =
        raw.takeIf(CinemaLabSources::isSafeHttps)

    fun parseVideos(raw: String, sourceId: String, genre: String): CinemaRutubePage {
        val obj = JSONObject(raw)
        val data = obj.optJSONArray("results") ?: JSONArray()
        val films = mutableListOf<CinemaTitle>()
        for (i in 0 until minOf(data.length(), 100)) {
            val item = data.optJSONObject(i) ?: continue
            val id = item.optString("id").trim()
            if (!id.matches(Regex("[a-fA-F0-9]{32}"))) continue
            if (item.optBoolean("is_paid", false) || item.optBoolean("is_hidden", false)) continue
            val title = item.optString("title").trim().take(170)
            if (title.isBlank()) continue
            val category = item.optJSONObject("category")?.optString("name").orEmpty()
            val season = item.optInt("season", 0)
            val episode = item.optInt("episode", 0).takeIf { it > 0 }
                ?: item.optInt("series", 0)
            val showId = item.optInt("tv_show_id", 0)
            val isSeries = category.contains("сериал", true) ||
                season > 0 || episode > 0 || showId > 0 ||
                Regex("\\d+\\s*сезон|\\d+\\s*сери", RegexOption.IGNORE_CASE)
                    .containsMatchIn(title)
            val originalDesc = item.optString("description")
            val shortDescription = Jsoup.parse(originalDesc).text().take(480)
            val cover = safeImage(item.optString("thumbnail_url"))
                ?: safeImage(item.optString("picture_url"))
            films.add(CinemaTitle(
                id = id.lowercase(),
                sourceId = sourceId,
                title = title,
                kind = if (isSeries) CinemaKind.SERIES else CinemaKind.MOVIE,
                year = Regex("(?:19|20)\\d{2}").find(title)?.value?.toIntOrNull(),
                description = shortDescription.ifBlank {
                    "Описание фильма пока не загружено"
                },
                poster = cover,
                genre = genre,
                streamUrl = null,
                episodes = emptyList(),
                attribution = "Каталог",
                pageUrl = "https://rutube.ru/video/$id/",
                seasonNumber = season.takeIf { it > 0 },
                episodeNumber = episode.takeIf { it > 0 },
                tvShowId = showId.takeIf { it > 0 }
            ))
        }
        val next = obj.optString("next").takeIf(::permittedApi)
        return CinemaRutubePage(films.distinctBy { it.id }, next)
    }

    suspend fun loadShelf(
        shelf: CinemaRutubeShelf, sourceId: String, pageUrl: String? = null
    ): CinemaRutubePage = withContext(Dispatchers.IO) {
        val address = pageUrl ?: shelf.url
        parseVideos(get(address), sourceId, shelf.title)
    }

    /**
     * Page through public search results instead of stopping at the first 50.
     * A non-empty first page is not evidence that all episodes were found.
     */
    suspend fun searchEpisodesPages(
        query: String, sourceId: String, maxPages: Int = 3
    ): List<CinemaTitle> = withContext(Dispatchers.IO) {
        val term = query.trim().take(90)
        if (term.length < 3) return@withContext emptyList()
        val found = mutableListOf<CinemaTitle>()
        val encoded = URLEncoder.encode(term, "UTF-8")
        val base = "https://rutube.ru/api/search/video/?query=" +
            encoded + "&page="
        var next: String? = base + "1&limit=50"
        val visited = hashSetOf<String>()
        for (page in 1..maxPages.coerceIn(1, 4)) {
            val address = next ?: break
            if (!visited.add(address)) break
            val loaded = try {
                parseVideos(get(address), sourceId, "Поиск RUTUBE")
            } catch (cancel: kotlinx.coroutines.CancellationException) {
                throw cancel
            } catch (_: Exception) {
                break
            }
            found.addAll(loaded.films)
            next = loaded.next ?: if (loaded.films.size >= 50) {
                base + (page + 1) + "&limit=50"
            } else null
        }
        found.distinctBy { it.key }
    }

    /** Search the connected RUTUBE source, not just currently visible cards. */
    suspend fun search(
        title: String, sourceId: String, shelves: List<CinemaRutubeShelf>
    ): List<CinemaTitle> = coroutineScope {
        val clean = title.trim().take(90)
        if (clean.length < 2) return@coroutineScope emptyList()
        val url = "https://rutube.ru/api/search/video/?query=" +
            URLEncoder.encode(clean, "UTF-8") + "&page=1&limit=50"
        val result = runCatching {
            withContext(Dispatchers.IO) {
                parseVideos(get(url), sourceId, "Поиск RUTUBE").films
            }
        }.getOrDefault(emptyList())
        if (result.isNotEmpty()) return@coroutineScope result.take(80)

        // Search API may return 0 to automated clients. In that case search
        // across several actual RUTUBE shelves; do not pretend global coverage.
        val preferred = shelves.filter {
            it.section == "Главная" || it.section == "По жанрам"
        }.distinctBy { it.url }.take(14)
        preferred.map { shelf ->
            async(Dispatchers.IO) {
                runCatching { loadShelf(shelf, sourceId).films }
                    .getOrDefault(emptyList())
            }
        }.awaitAll().flatten()
            .distinctBy { it.key }
            .filter { it.title.contains(clean, ignoreCase = true) ||
                it.description.contains(clean, ignoreCase = true) }
            .take(80)
    }
}
