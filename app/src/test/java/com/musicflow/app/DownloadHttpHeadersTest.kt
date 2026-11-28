package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class DownloadHttpHeadersTest {
    @Test fun acceptedSafeHttpsRefererAndAgent() {
        assertEquals(mapOf("Referer" to "https://example.org/watch",
            "User-Agent" to "MusicFlowTEST"), DownloadHttpHeaders.validated(
            "https://example.org/watch", "MusicFlowTEST"))
    }

    @Test fun blankHeadersAreNotSent() {
        assertTrue(DownloadHttpHeaders.validated("", " ").isEmpty())
    }

    @Test fun blocksHeaderInjectionAndNonHttpsReferer() {
        for (ref in listOf("http://example.org", "https://a.test\r\nCookie: stolen",
            "https://user:pass@host.test/")) {
            assertThrows(IllegalArgumentException::class.java) {
                DownloadHttpHeaders.validated(ref, "")
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            DownloadHttpHeaders.validated("", "agent\nInjected: yes")
        }
    }
}
