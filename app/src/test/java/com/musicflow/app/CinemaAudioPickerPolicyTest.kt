package com.musicflow.app
import org.junit.Assert.*
import org.junit.Test
class CinemaAudioPickerPolicyTest {
 @Test fun selectorIsAvailableEvenForSingleTrackToExplainMissingDub() {
  assertTrue(CinemaAudioPickerPolicy.canOpen(0))
  assertTrue(CinemaAudioPickerPolicy.canOpen(1))
  assertTrue(CinemaAudioPickerPolicy.canOpen(3))
  assertEquals("Видеопоток пока не сообщил аудиодорожки", CinemaAudioPickerPolicy.status(0))
  assertEquals("Источник предоставляет только одну аудиодорожку. Другую озвучку выбрать нельзя.",
    CinemaAudioPickerPolicy.status(1))
  assertEquals("Доступно 3 аудиодорожки", CinemaAudioPickerPolicy.status(3))
 }
 @Test fun onlyActuallyPublishedAlternatesCountAsVoiceChoices() {
  assertFalse(CinemaAudioPickerPolicy.hasAlternates(1))
  assertTrue(CinemaAudioPickerPolicy.hasAlternates(2))
 }
}