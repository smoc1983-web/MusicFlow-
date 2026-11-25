package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaRutubeCatalogTest {
    private val videoId = "1dcc35d6575bc881dc2ece8c1efcdc35"

    @Test fun parsedGenreTabsPreserveRealCategoryNamesAndWhitelistedUrls() {
        val json = """
        {"tabs":[
          {"name":"Главная","resources":[
            {"name":"Рекомендуем","url":"https://rutube.ru/api/tags/video/123/?limit=20"}]},
          {"name":"По жанрам","resources":[
            {"name":"Боевики","url":"https://rutube.ru/api/tags/video/2546/?limit=20"},
            {"name":"Комедии","url":"https://rutube.ru/api/tags/video/2548/?limit=20"},
            {"name":"Небезопасный","url":"http://localhost/api/video/1/"}]},
          {"name":"По годам","resources":[
            {"name":"2025","url":"https://rutube.ru/api/tags/video/8564/"}]}
        ]}
        """.trimIndent()
        val shelves = CinemaRutubeCatalog.parseShelves(json)
        assertEquals(4, shelves.size)
        assertEquals("Боевики", shelves[1].title)
        assertEquals("По жанрам", shelves[1].section)
        assertFalse(CinemaRutubeCatalog.permittedApi("https://example.org/api/video/1/"))
        assertFalse(CinemaRutubeCatalog.permittedApi("http://rutube.ru/api/video/1/"))
        assertTrue(CinemaRutubeCatalog.permittedApi(
            "https://rutube.ru/api/tags/video/2546/?limit=20"))
    }

    @Test fun apiCardsHaveRealNamesAndThumbnailPosters() {
        val raw = """
        {"results":[
         {"id":"$videoId","title":"Бывшие, 1 сезон, 1 серия",
          "description":"Описание серии","thumbnail_url":"https://pic.example.org/cover.jpg",
          "picture_url":"","category":{"name":"Сериалы"},"season":1,"episode":1},
         {"id":"bad","title":"Unknown"},
         {"id":"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa","title":"Платный фильм","is_paid":true}
        ],"next":"https://rutube.ru/api/tags/video/1/?page=2"}
        """.trimIndent()
        val page = CinemaRutubeCatalog.parseVideos(raw, "source", "Мелодрамы")
        assertEquals(1, page.films.size)
        val item = page.films[0]
        assertEquals("Бывшие, 1 сезон, 1 серия", item.title)
        assertEquals("https://pic.example.org/cover.jpg", item.poster)
        assertEquals(CinemaKind.SERIES, item.kind)
        assertEquals("Мелодрамы", item.genre)
        assertEquals("https://rutube.ru/video/$videoId/", item.pageUrl)
        assertFalse(item.hasPlayback)
        assertTrue(page.next?.contains("page=2") == true)
    }

    @Test fun tvShowSeasonAndEpisodeMetadataAreExtracted() {
        val rel = """{"tv":{"id":28,"name":"Интерны","type_id":3},"season":1,"episode":3}"""
        assertEquals(28, CinemaRutubeSeries.showIdFromRelation(rel))
        val show = CinemaRutubeSeries.parseShow(
            """{"id":28,"name":"Интерны","seasons_count":5,"description":"Описание"}"""
        )
        assertEquals(5, show.seasons)
        val episodes = CinemaRutubeSeries.parseEpisodes(
            """
             {"results":[
               {"id":"11111111111111111111111111111111","title":"Третья серия",
                "season":1,"episode":3},
               {"id":"22222222222222222222222222222222","title":"Первая серия",
                "season":1,"episode":1}
             ]}
            """.trimIndent(), "source")
        assertEquals(2, episodes.size)
        assertEquals(1, episodes[0].episodeNumber)
        assertEquals(3, episodes[1].episodeNumber)
    }

    @Test fun publicVideoMetadataCanIdentifySeriesWithoutHtmlHeuristics() {
        val original = CinemaTitle(
            videoId, "source", "Видео · 1dcc35d6", CinemaKind.MOVIE,
            null, "", null, "", null, emptyList(), "",
            pageUrl = "https://rutube.ru/video/$videoId/"
        )
        val resolved = CinemaRutubeSource.fromMeta(original,
            """{"id":"$videoId","title":"Бывшие, 1 сезон, 1 серия",
            "thumbnail_url":"https://pic.example.org/series.jpg",
            "is_serial":true,"season":1,"episode":1,"tv_show_id":1277688}"""
        )
        assertEquals("Бывшие, 1 сезон, 1 серия", resolved.title)
        assertEquals("https://pic.example.org/series.jpg", resolved.poster)
        assertEquals(CinemaKind.SERIES, resolved.kind)
        assertEquals(1277688, resolved.tvShowId)
    }
}
