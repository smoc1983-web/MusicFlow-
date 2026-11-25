package com.musicflow.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Reserve-only station directory must still reject private and malformed stream URLs. */
class RussianRadioCatalogPolicyTest {
    @Test fun publicRadioUrlsAreAccepted() {
        assertTrue(RussianRadioDirectory.isPublicStream(
            "https://radio.example.org/live.mp3"))
        assertTrue(RussianRadioDirectory.isPublicStream(
            "http://radio.example.org/live"))
    }

    @Test fun localAndPrivateStreamUrlsAreRejected() {
        assertFalse(RussianRadioDirectory.isPublicStream(
            "http://127.0.0.1:8000/audio"))
        assertFalse(RussianRadioDirectory.isPublicStream(
            "http://192.168.1.1/stream"))
        assertFalse(RussianRadioDirectory.isPublicStream(
            "file:///private/stream"))
    }
}
