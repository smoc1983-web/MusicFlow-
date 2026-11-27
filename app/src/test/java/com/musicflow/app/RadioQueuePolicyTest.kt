package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class RadioQueuePolicyTest {
    private fun station(id: String, stream: String = "https://radio.example.org/$id.mp3") =
        RussianRadioStation(id, "Радио $id", stream, null, "", "", 0)

    @Test fun navigationUsesVisibleListAndSelectedIndex() {
        val a = station("a")
        val b = station("b")
        val c = station("c")
        val (ordered, index) = RadioQueuePolicy.playlist(b, listOf(a, b, c))
        assertEquals(listOf("a", "b", "c"), ordered.map { it.uuid })
        assertEquals(1, index)
        assertTrue(RadioQueuePolicy.canNavigate(ordered.size))
    }

    @Test fun filtersUntrustedStreamsAndDuplicateUuids() {
        val a = station("a")
        val bad = station("x", "http://127.0.0.1/private")
        val (items, index) = RadioQueuePolicy.playlist(a, listOf(a, bad, a))
        assertEquals(listOf("a"), items.map { it.uuid })
        assertEquals(0, index)
        assertFalse(RadioQueuePolicy.canNavigate(items.size))
    }

    @Test fun stationAbsentFromCurrentFilterStaysAlone() {
        val selected = station("selected")
        val (items, index) = RadioQueuePolicy.playlist(selected, listOf(station("other")))
        assertEquals(listOf(selected), items)
        assertEquals(0, index)
    }
}
