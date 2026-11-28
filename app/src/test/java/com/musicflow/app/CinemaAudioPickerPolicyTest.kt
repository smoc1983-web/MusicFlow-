package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test

class CinemaAudioPickerPolicyTest {
    @Test fun dubbingSelectorOnlyAppearsForRealAlternatives() {
        assertFalse(CinemaAudioPickerPolicy.canOpen(0))
        assertFalse(CinemaAudioPickerPolicy.canOpen(1))
        assertTrue(CinemaAudioPickerPolicy.canOpen(2))
        assertTrue(CinemaAudioPickerPolicy.canOpen(3))
        assertFalse(CinemaAudioPickerPolicy.hasAlternates(0))
        assertFalse(CinemaAudioPickerPolicy.hasAlternates(1))
        assertTrue(CinemaAudioPickerPolicy.hasAlternates(2))
    }

    @Test fun messageNeverInventsAudioVariants() {
        assertEquals("Видеопоток пока не сообщил аудиодорожки",
            CinemaAudioPickerPolicy.status(0))
        assertEquals("Источник предоставляет только одну аудиодорожку. Другую озвучку выбрать нельзя.",
            CinemaAudioPickerPolicy.status(1))
        assertEquals("Доступно 3 аудиодорожки", CinemaAudioPickerPolicy.status(3))
    }
}
