package com.musicflow.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class SingleMusicSourceTest {
    @Test fun selectedSourceIsTheOnlyProviderCalled() = runBlocking {
        val calls = mutableListOf<String>()
        val registry = mapOf<String, suspend (String) -> List<String>>(
            "a" to { query -> calls += "a:$query"; listOf("first") },
            "b" to { query -> calls += "b:$query"; listOf("second") }
        )
        assertEquals(listOf("second"), searchOneProvider("b", "Queen", registry))
        assertEquals(listOf("b:Queen"), calls)
    }

    @Test fun missingProviderNeverFallsBack() = runBlocking {
        var called = false
        val registry = mapOf<String, suspend (String) -> List<String>>(
            "a" to { called = true; listOf("first") }
        )
        try {
            searchOneProvider("unknown", "Queen", registry)
            fail("Unknown provider must be rejected")
        } catch (_: IllegalArgumentException) {}
        assertFalse(called)
    }
}
