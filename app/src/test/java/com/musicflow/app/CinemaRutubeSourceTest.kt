package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaRutubeSourceTest {
    private val id = "1dcc35d6575bc881dc2ece8c1efcdc35"

    private fun original() = CinemaTitle(
        id = id, sourceId = "starter-rutube", title = "Бывшие · серия 1",
        kind = CinemaKind.SERIES, year = null, description = "Из каталога",
        poster = null, genre = "Сериалы", streamUrl = null,
        episodes = emptyList(), attribution = "RUTUBE",
        pageUrl = "https://rutube.ru/video/$id/"
    )

    @Test fun defaultIsRealRutubeMoviesSeriesFeed() {
        assertEquals("https://rutube.ru/feeds/movies-serials/",
            CinemaNativeSources.FIRST_PAGE)
        assertTrue(CinemaRutubeSource.isFeed(CinemaNativeSources.FIRST_PAGE))
        assertEquals(CinemaSourceFormat.RUTUBE_FEED,
            CinemaNativeSources.chooseFormat(CinemaNativeSources.FIRST_PAGE))
        assertFalse(CinemaRutubeSource.isFeed("https://rutube.ru/feeds/movies/"))
    }

    @Test fun feedCardsHaveValidIdsNoFakeMediaAndDeduplicate() {
        val html = """
          <html><body>
            <article>
              <a href="/video/$id/">
                <img src="https://pic.rutube.ru/video/poster.jpg"
                  alt="Бывшие, 1 сезон, 1 серия" />
              </a>
            </article>
            <a href="/video/$id/">Дубль того же видео</a>
            <a href="/live/video/9ae8e8a6dc58bdad66190475f9872ecd/">Прямой эфир</a>
            <a href="https://bad.example.org/video/1234567890abcdef1234567890abcdef/">
                Чужой домен</a>
          </body></html>
        """.trimIndent()
        val items = CinemaRutubeSource.parseFeed(html, "starter-rutube")
        assertEquals(1, items.size)
        assertEquals("Бывшие, 1 сезон, 1 серия", items[0].title)
        assertEquals("https://rutube.ru/video/$id/", items[0].pageUrl)
        assertNull(items[0].streamUrl)
        assertEquals("https://pic.rutube.ru/video/poster.jpg", items[0].poster)
    }

    @Test fun videoIdCanOnlyBelongToRutubeAndHasHexLength32() {
        assertEquals(id, CinemaRutubeSource.videoId("https://rutube.ru/video/$id/"))
        assertEquals(id, CinemaRutubeSource.videoId(
            "https://rutube.ru/play/embed/$id"))
        assertNull(CinemaRutubeSource.videoId("https://example.org/video/$id/"))
        assertNull(CinemaRutubeSource.videoId("https://rutube.ru/live/video/$id/"))
        assertNull(CinemaRutubeSource.videoId("https://rutube.ru/video/not-a-video/"))
        assertEquals(
            "https://rutube.ru/api/play/options/$id/?format=json",
            CinemaRutubeSource.apiUrl(id)
        )
    }

    @Test fun publicPlayOptionsCanProduceNativeHlsStream() {
        val hls = "https://stream.rutube.example.org/abc/master.m3u8?sig=test"
        val raw = """
            {"video_balancer":{"m3u8":"$hls"},
             "title":"Бывшие, 1 сезон, 1 серия",
             "description":"Видео доступно"}
        """.trimIndent()
        val item = CinemaRutubeSource.fromPlayOptions(original(), raw)
        assertTrue(item.hasPlayback)
        assertEquals(hls, item.streamUrl)
        assertTrue(item.playbackDiagnostic.orEmpty().contains("HLS"))
    }

    @Test fun paidPrivateAndAbsentManifestDoNotBecomePlayable() {
        val publicHls = "https://cdn.example.org/video/master.m3u8"
        val restricted = """
            {"is_paid":true,"video_balancer":{"m3u8":"$publicHls"}}
        """.trimIndent()
        assertFalse(CinemaRutubeSource.fromPlayOptions(original(), restricted).hasPlayback)
        val hidden = """
            {"is_hidden":true,"video_balancer":{"m3u8":"$publicHls"}}
        """.trimIndent()
        assertFalse(CinemaRutubeSource.fromPlayOptions(original(), hidden).hasPlayback)
        val missing = """{"video_balancer":{"default":"https://example.org/video"}}"""
        assertFalse(CinemaRutubeSource.fromPlayOptions(original(), missing).hasPlayback)
        val unsafe = """{"video_balancer":{"m3u8":"http://localhost/private.m3u8"}}"""
        assertFalse(CinemaRutubeSource.fromPlayOptions(original(), unsafe).hasPlayback)
    }
}
