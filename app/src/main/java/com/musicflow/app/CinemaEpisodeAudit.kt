package com.musicflow.app

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

internal data class CinemaExpectedEpisode(
    val season: Int,
    val number: Int,
    val title: String,
    val airDate: String?
)

internal data class CinemaEpisodeGuide(
    val id: Int,
    val name: String,
    val url: String,
    val episodes: List<CinemaExpectedEpisode>
) {
    fun seasons(): List<Int> = episodes.map { it.season }.distinct().sorted()
    fun inSeason(season: Int): List<CinemaExpectedEpisode> =
        episodes.filter { it.season == season }.sortedBy { it.number }
}

internal data class CinemaSeasonCoverage(
    val expected: List<CinemaExpectedEpisode>,
    val found: List<CinemaTitle>,
    val missing: List<Int>,
    val verifiedGuide: Boolean
) {
    val foundNumbers: Set<Int> get() = found.mapNotNull { it.episodeNumber }.toSet()
    val expectedCount: Int get() = expected.size
    val foundCount: Int get() = expected.count { it.number in foundNumbers }
}

/** Metadata only: TVmaze never supplies playback streams or authorization. */
internal object CinemaEpisodeAudit {
    private const val MAX_BYTES = 2_000_000
    private val cache = ConcurrentHashMap<String, CinemaEpisodeGuide>()

    private fun normalized(value: String): String = value.lowercase(Locale.ROOT)
        .replace(Regex("""[^a-z0-9]+"""), " ").trim().replace(Regex("""\s+"""), " ")

    /** Use only supplied Latin names, never invent a translation. */
    fun englishHints(showName: String, episodeTitle: String): List<String> =
        listOf(showName, episodeTitle.substringAfterLast('/', ""),
            episodeTitle.substringAfterLast('|', ""))
            .map { it.trim().take(100) }
            .filter { value ->
                val letters = value.count(Char::isLetter)
                letters >= 4 && value.count { it in 'a'..'z' || it in 'A'..'Z' } >=
                    letters * 3 / 4 && value.length in 4..90
            }.distinct()

    /** Exact match distinguishes American Horror Story from American Horror Stories. */
    fun matchShowId(results: String, title: String): Pair<Int, String>? {
        val wanted = normalized(title)
        if (wanted.length < 4) return null
        val json = JSONArray(results)
        for (i in 0 until minOf(json.length(), 20)) {
            val show = json.optJSONObject(i)?.optJSONObject("show") ?: continue
            val id = show.optInt("id")
            val name = show.optString("name")
            if (id > 0 && normalized(name) == wanted) return id to name
        }
        return null
    }

    fun parseEpisodes(id: Int, name: String, raw: String): CinemaEpisodeGuide {
        val array = JSONArray(raw)
        val entries = ArrayList<CinemaExpectedEpisode>()
        for (i in 0 until minOf(array.length(), 5000)) {
            val e = array.optJSONObject(i) ?: continue
            val season = e.optInt("season")
            val number = e.optInt("number")
            // Season zero / unnumbered specials are excluded from aired season counts.
            if (season !in 1..30 || number !in 1..300) continue
            entries.add(CinemaExpectedEpisode(
                season, number, e.optString("name").take(130),
                e.optString("airdate").takeIf { it.matches(Regex("""\d{4}-\d{2}-\d{2}""")) }
            ))
        }
        return CinemaEpisodeGuide(
            id, name.take(140), "https://www.tvmaze.com/shows/" + id,
            entries.distinctBy { it.season to it.number }
                .sortedWith(compareBy({ it.season }, { it.number }))
        )
    }

    fun coverage(
        guide: CinemaEpisodeGuide?,
        season: Int,
        found: List<CinemaTitle>
    ): CinemaSeasonCoverage {
        val matches = found.map(CinemaSeriesSearch::enrich)
            .filter { it.seasonNumber == season }.distinctBy { it.key }
        val listed = guide?.inSeason(season).orEmpty()
        val inferred = if (listed.isEmpty()) {
            val high = matches.mapNotNull { it.episodeNumber }.maxOrNull()
                ?.coerceIn(0, 300) ?: 0
            (1..high).map { CinemaExpectedEpisode(season, it, "", null) }
        } else emptyList()
        val expected = if (listed.isNotEmpty()) listed else inferred
        val foundNumbers = matches.mapNotNull { it.episodeNumber }.toSet()
        val today = java.time.LocalDate.now().toString()
        return CinemaSeasonCoverage(expected, matches,
            expected.filter { it.airDate == null || it.airDate <= today }
                .map { it.number }.filterNot { it in foundNumbers },
            listed.isNotEmpty())
    }

    private fun get(path: String): String {
        require(path.startsWith("/") && !path.contains("://"))
        val conn = URL("https://api.tvmaze.com" + path)
            .openConnection() as HttpURLConnection
        conn.connectTimeout = 5_000
        conn.readTimeout = 9_000
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("Accept", "application/json")
        conn.setRequestProperty("User-Agent", "MusicFlow-Cinema-EpisodeAudit/0.7.42")
        try {
            check(conn.responseCode == 200) { "TVmaze HTTP " + conn.responseCode }
            val out = ByteArrayOutputStream()
            conn.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    check(out.size() + count <= MAX_BYTES) { "TVmaze ответ слишком большой" }
                    out.write(buffer, 0, count)
                }
            }
            return out.toString("UTF-8")
        } finally {
            conn.disconnect()
        }
    }

    suspend fun lookup(showName: String, episodeTitle: String): CinemaEpisodeGuide? =
        withContext(Dispatchers.IO) {
            for (hint in englishHints(showName, episodeTitle)) {
                cache[normalized(hint)]?.let { return@withContext it }
                try {
                    val term = URLEncoder.encode(hint, "UTF-8")
                    val matched = matchShowId(get("/search/shows?q=" + term), hint)
                        ?: continue
                    val guide = parseEpisodes(matched.first, matched.second,
                        get("/shows/" + matched.first + "/episodes"))
                    if (guide.episodes.isNotEmpty()) {
                        cache[normalized(hint)] = guide
                        return@withContext guide
                    }
                } catch (cancel: CancellationException) {
                    throw cancel
                } catch (_: Exception) {
                    // If guide is unavailable, continue using source-specific results.
                }
            }
            null
        }
}
