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

    @Test fun blocksInsecureOrLocalImageAddresses() {
        assertNull(RadioArtworkCatalog.findExactLogo(
            """[{"name":"Радио Ваня","favicon":"http://192.168.1.1/icon.png"}]""", "Радио Ваня"))
        assertNull(RadioArtworkCatalog.findExactLogo(
            """[{"name":"Радио Ваня","favicon":"https://localhost/icon.png"}]""", "Радио Ваня"))
        assertTrue(RadioArtworkCatalog.isSafeLogo("https://cdn.example.com/logo.png"))
        assertFalse(RadioArtworkCatalog.isSafeLogo("javascript:alert(1)"))
    }
}
