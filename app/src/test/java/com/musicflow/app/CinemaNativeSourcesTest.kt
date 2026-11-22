package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaNativeSourcesTest {
    @Test fun userPageIsInitialSourceAndIsNotMisclassifiedAsStream() {
        assertEquals(
            "https://ff.sanfilm2.link/",
            CinemaNativeSources.FIRST_PAGE
        )
        assertEquals(CinemaSourceFormat.WEB_PAGE,
            CinemaNativeSources.chooseFormat(CinemaNativeSources.FIRST_PAGE))
        assertEquals(CinemaSourceFormat.DIRECT_MEDIA,
            CinemaNativeSources.chooseFormat("https://example.org/film.mp4"))
        assertEquals(CinemaSourceFormat.DIRECT_MEDIA,
            CinemaNativeSources.chooseFormat("https://example.org/a.m3u8?key=abc"))
        assertEquals(CinemaSourceFormat.M3U_PLAYLIST,
            CinemaNativeSources.chooseFormat("https://example.org/playlist.m3u"))
        assertEquals(CinemaSourceFormat.CINEMA_JSON,
            CinemaNativeSources.chooseFormat("https://example.org/catalog.json"))
    }

    @Test fun htmlOpenGraphVideoMetadataIsParsedButIframeIsNotTreatedAsMedia() {
        val url = "https://films.example.org/film-2026.html"
        val html = """
            <html><head><title>Fallback title</title>
            <meta property="og:title" content="Фильм 2026" />
            <meta property="og:image" content="/cover.jpg" />
            <meta property="og:description" content="Краткое описание" />
            </head><body><video controls>
            <source src="/stream/film.mp4" type="video/mp4" />
            </video></body></html>
        """.trimIndent()
        val title = CinemaNativeSources.parseHtml(html, url, "id-1", "Мой фильм")
        assertEquals("Фильм 2026", title.title)
        assertEquals("https://films.example.org/cover.jpg", title.poster)
        assertEquals("https://films.example.org/stream/film.mp4", title.streamUrl)
        assertTrue(title.hasPlayback)
        val iframe = """<html><title>Другое кино</title>
            <iframe src="https://player.example.org/embed"></iframe></html>"""
        val unavailable = CinemaNativeSources.parseHtml(iframe, url, "id-2", "Другое")
        assertFalse(unavailable.hasPlayback)
    }

    @Test fun sourceUrlValidationDoesNotAcceptScriptsOrInternalHosts() {
        assertTrue(CinemaNativeSources.register("", CinemaNativeSources.FIRST_PAGE).name.isNotBlank())
        assertTrue(runCatching {
            CinemaNativeSources.register("", "http://example.org/movie.html")
        }.isFailure)
        assertTrue(runCatching {
            CinemaNativeSources.register("", "https://example.org/plugin.js")
        }.isFailure)
        assertTrue(runCatching {
            CinemaNativeSources.register("", "https://192.168.1.2/film.mp4")
        }.isFailure)
    }

    @Test fun m3uVideosStillUseNativeMedia3Urls() {
        val source = """#EXTM3U
            #EXTINF:-1 group-title="Фильмы",Видео
            https://example.org/films/test.mp4
            #EXTINF:-1,Недопустимая ссылка
            http://localhost/test.mp4""".trimIndent()
        val items = CinemaNativeSources.parseM3u(source, "my-source")
        assertEquals(1, items.size)
        assertEquals("Видео", items[0].title)
        assertEquals("https://example.org/films/test.mp4", items[0].streamUrl)
    }

    @Test fun homepageLinksBecomeMovieCardsInsteadOfOneFakePlayableFilm() {
        val base = "https://ff.sanfilm2.link/"
        val home = """
          <html><head><title>Кино онлайн</title></head><body>
          <div class="shortstory"><a href="/69359-zavodchica-2026.html">
          <img src="/uploads/zavodchica.jpg" alt="Заводчица (2026)" /></a></div>
          <a href="/contact.html">Контакты</a>
          <a href="https://other.example.org/12345-movie.html">Посторонний сайт</a>
          <a href="javascript:alert('unsafe')">Небезопасная ссылка</a>
          </body></html>
        """.trimIndent()
        val entries = CinemaNativeSources.parseSiteIndex(home, base, "starter-site")
        assertEquals(1, entries.size)
        assertEquals("Заводчица (2026)", entries[0].title)
        assertEquals("https://ff.sanfilm2.link/uploads/zavodchica.jpg",
            entries[0].poster)
        assertEquals("https://ff.sanfilm2.link/69359-zavodchica-2026.html",
            entries[0].pageUrl)
        assertFalse(entries[0].hasPlayback)
        assertEquals("starter-site", entries[0].sourceId)
    }

    @Test fun homepageWithoutMovieCardsStaysEmpty() {
        val result = CinemaNativeSources.parseSiteIndex(
            "<html><title>Каталог</title><a href='/login'>Вход</a></html>",
            CinemaNativeSources.FIRST_PAGE, "starter-site")
        assertTrue(result.isEmpty())
    }

}
