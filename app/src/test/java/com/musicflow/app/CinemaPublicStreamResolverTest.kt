package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaPublicStreamResolverTest {
    private val page = "https://cinema.example.org/film-2026.html"

    @Test fun html5VideoCanBeRelativeAndPublic() {
        val html = """<html><video><source src="/media/master.m3u8?time=10"
            type="application/x-mpegURL"></video></html>"""
        val result = CinemaPublicStreamResolver.scan(html, page)
        assertEquals("https://cinema.example.org/media/master.m3u8?time=10",
            result.mediaUrl)
        assertEquals("HTML5", result.method)
    }

    @Test fun openGraphVideoCanBePlayedByNativePlayer() {
        val html = """<meta property="og:video:secure_url"
                    content="https://cdn.example.org/watch/film.mp4?key=ABC">"""
        val scan = CinemaPublicStreamResolver.scan(html, page)
        assertEquals("https://cdn.example.org/watch/film.mp4?key=ABC", scan.mediaUrl)
        assertEquals("OpenGraph", scan.method)
    }

    @Test fun videoObjectContentUrlFromStructuredDataIsUsed() {
        val html = """
            <html><script type="application/ld+json">
             {"@context":"https://schema.org","@type":"VideoObject",
               "name":"Кино", "contentUrl":"https://cdn.example.org/video.mp4"}
            </script></html>
        """.trimIndent()
        assertEquals("https://cdn.example.org/video.mp4",
            CinemaPublicStreamResolver.scan(html, page).mediaUrl)
    }

    @Test fun literalJsFileUrlIsReadAsDataWithoutExecutingIt() {
        val html = """
            <script>
              var player = { file: "https:\/\/cdn.example.org\/tv\/stream.m3u8?x=1" };
            </script>
        """.trimIndent()
        val scan = CinemaPublicStreamResolver.scan(html, page)
        assertEquals("https://cdn.example.org/tv/stream.m3u8?x=1", scan.mediaUrl)
        assertEquals("открытый параметр плеера", scan.method)
    }

    @Test fun publicIframeMarkupCanContainPlainDirectVideoUrl() {
        val html = """<html><iframe src="https://player.example.net/embed/123"></iframe></html>"""
        var called = 0
        val scan = CinemaPublicStreamResolver.resolve(html, page) { address ->
            called++
            assertEquals("https://player.example.net/embed/123", address)
            """<video src="https://cdn.example.net/123/master.m3u8"></video>"""
        }
        assertEquals(1, called)
        assertEquals(1, scan.inspected)
        assertEquals("https://cdn.example.net/123/master.m3u8", scan.mediaUrl)
    }

    @Test fun deniedIframeOrJsOnlyPageDoesNotInventVideoUrl() {
        val html = """<html><iframe src="https://embed.example.net/blocked"></iframe>
            <script src="https://embed.example.net/player.js"></script></html>"""
        val scan = CinemaPublicStreamResolver.resolve(html, page) {
            throw IllegalStateException("HTTP 403")
        }
        assertNull(scan.mediaUrl)
        assertEquals(1, scan.inspected)
        assertTrue(scan.report().contains("Прямая ссылка не найдена"))
    }

    @Test fun privateHttpAndUnrelatedJpegAreNotVideo() {
        val html = """<video src="http://192.168.0.2/private.mp4"></video>
          <meta property="og:image" content="https://images.example.org/image.jpg">
          <script>var file = "javascript:alert(1)"</script>"""
        assertFalse(CinemaPublicStreamResolver.scan(html, page).mediaUrl != null)
    }
}
