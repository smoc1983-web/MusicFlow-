package com.musicflow.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CinemaRutubeRedirectTest {
    @Test fun canonicalMoviesFeedUsesTrailingSlash() {
        assertEquals(
            "https://rutube.ru/api/feeds/movies/?format=json",
            CinemaRutubeCatalog.redirectTarget(
                "https://rutube.ru/api/feeds/movies?format=json",
                "/api/feeds/movies/?format=json"
            )
        )
    }

    @Test fun validApiRedirectCanChangeToWwwButNeverOutsidePublicApi() {
        assertEquals(
            "https://www.rutube.ru/api/feeds/movies/?format=json",
            CinemaRutubeCatalog.redirectTarget(
                "https://rutube.ru/api/feeds/movies?format=json",
                "https://www.rutube.ru/api/feeds/movies/?format=json"
            )
        )
        assertTrue(CinemaRutubeCatalog.permittedApi(
            "https://rutube.ru/api/feeds/movies/?format=json"))
    }

    @Test fun redirectsToForeignHostsOrNonHttpsSchemesAreBlocked() {
        val start = "https://rutube.ru/api/feeds/movies/?format=json"
        for (bad in listOf(
            "http://rutube.ru/api/feeds/movies/?format=json",
            "https://example.net/api/feeds/movies/",
            "https://rutube.ru/admin/",
            "https://rutube.ru:8443/api/feeds/movies/",
            "https://user:password@rutube.ru/api/feeds/movies/",
            "file:///android_asset/internal",
            "javascript:alert(1)",
            "https://rutube.ru/api/feeds/movies/#fragment",
            "//127.0.0.1/api/private"
        )) {
            assertNull("Must reject: $bad",
                CinemaRutubeCatalog.redirectTarget(start, bad))
        }
        assertNull(CinemaRutubeCatalog.redirectTarget(start, ""))
        assertNull(CinemaRutubeCatalog.redirectTarget(start, null))
    }

    @Test fun relativeApiRedirectsAreCanonicalizedWithinSameHost() {
        val start = "https://rutube.ru/api/feeds/movies/?format=json"
        assertEquals(
            "https://rutube.ru/api/feeds/movies/?page=2",
            CinemaRutubeCatalog.redirectTarget(start, "?page=2")
        )
        assertFalse(CinemaRutubeCatalog.permittedApi(
            "https://other.example.com/api/feeds/movies/"))
    }
}
