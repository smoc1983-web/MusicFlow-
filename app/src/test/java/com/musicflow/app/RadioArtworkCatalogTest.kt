package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class RadioArtworkCatalogTest {
    @Test fun picksOnlyExactPublicHttpsLogoAndNeverReplacesPlaylistStations() {
        val response = """
          [{"stationuuid":"x","name":"Радио Ваня FM",
            "favicon":"https://cdn.example.org/wrong.png"},
           {"stationuuid":"y","name":"Радио Ваня",
            "favicon":"https://images.example.org/vanya.png"}]
        """.trimIndent()
        assertEquals("https://images.example.org/vanya.png",
            RadioArtworkCatalog.findExactLogo(response, "Радио Ваня"))
        assertNull(RadioArtworkCatalog.findExactLogo(response,"Радио Ваня FM 2"))
    }


    @Test fun preservesMultipleExactStationArtworkCandidates() {
        val response = """
          [{"name":"Радио Волна","favicon":"https://cdn.example.org/dead.png"},
           {"name":"Радио Волна 2","favicon":"https://cdn.example.org/wrong.png"},
           {"name":"РАДИО ВОЛНА","favicon":"https://cdn.example.org/good.webp"},
           {"name":"Радио Волна","favicon":"https://cdn.example.org/dead.png"},
           {"name":"Радио Волна","favicon":"http://insecure.example.org/logo.png"}]
        """.trimIndent()
        assertEquals(
            listOf("https://cdn.example.org/dead.png",
                   "https://cdn.example.org/good.webp"),
            RadioArtworkCatalog.candidateLogos(response, "Радио Волна")
        )
    }

    @Test fun rejectsHtmlErrorPagesAsLogosButAllowsSupportedImageHeaders() {
        val html = "<html>not an image</html>".toByteArray()
        assertFalse(RadioArtworkCatalog.isSupportedImage(html, html.size))
        val png = byteArrayOf(137.toByte(), 80, 78, 71, 13, 10, 26, 10, 0, 0, 0, 0)
        val jpg = byteArrayOf(255.toByte(), 216.toByte(), 255.toByte(), 224.toByte())
        val webp = "RIFF1234WEBP".toByteArray()
        val gif = "GIF89a".toByteArray()
        assertTrue(RadioArtworkCatalog.isSupportedImage(png, png.size))
        assertTrue(RadioArtworkCatalog.isSupportedImage(jpg, jpg.size))
        assertTrue(RadioArtworkCatalog.isSupportedImage(webp, webp.size))
        assertTrue(RadioArtworkCatalog.isSupportedImage(gif, gif.size))
        assertFalse(RadioArtworkCatalog.isSupportedImage(png, 2))
    }

    @Test fun blocksInsecureOrLocalImageAddresses() {
        assertNull(RadioArtworkCatalog.findExactLogo(
            """[{"name":"Радио Ваня","favicon":"http://192.168.1.1/icon.png"}]""", "Радио Ваня"))
        assertNull(RadioArtworkCatalog.findExactLogo(
            """[{"name":"Радио Ваня","favicon":"https://localhost/icon.png"}]""", "Радио Ваня"))
        assertTrue(RadioArtworkCatalog.isSafeLogo("https://cdn.example.com/logo.png"))
        assertFalse(RadioArtworkCatalog.isSafeLogo("javascript:alert(1)"))
    }
}
