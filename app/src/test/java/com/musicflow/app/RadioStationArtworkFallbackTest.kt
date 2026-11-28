package com.musicflow.app
import org.junit.Assert.*
import org.junit.Test
class RadioStationArtworkFallbackTest {
 @Test fun allNineFallbackStylesChooseDeterministically() {
  val stations = listOf("Радио Песни", "Европа плюс", "Наше Радио", "FM", "JAZZ",
   "Камеди Радио", "Comedy", "Наше радио", "Дорожное", "Русское")
  stations.forEach { name ->
    val index = RadioStationArtworkFallback.artworkIndex(name)
    assertTrue(index in 0..8)
    assertEquals(index, RadioStationArtworkFallback.artworkIndex(name.uppercase()))
  }
 }

 @Test fun everyStationGetsReadableLettermarkEvenWithoutLogo() {
  assertEquals("РП", RadioStationArtworkFallback.initials("Радио Песни"))
  assertEquals("Е+", RadioStationArtworkFallback.initials("Европа плюс"))
  assertEquals("НР", RadioStationArtworkFallback.initials("Наше Радио"))
  assertEquals("FM", RadioStationArtworkFallback.initials("FM"))
  assertEquals("♫", RadioStationArtworkFallback.initials(" "))
 }
 @Test fun excessSymbolsAreIgnoredAndLettersStayDeterministic() {
  assertEquals("КП", RadioStationArtworkFallback.initials("«Комсомольская Правда»"))
  assertEquals("M", RadioStationArtworkFallback.initials("  Music..."))
 }
 @Test fun stationVisualsUseStablePerStationColors() {
  assertEquals(
   RadioStationArtworkFallback.hue("Радио Песни"),
   RadioStationArtworkFallback.hue("РАДИО ПЕСНИ")
  )
  assertTrue(RadioStationArtworkFallback.hue("Радио Песни") in 0f..359f)
  assertNotEquals(
   RadioStationArtworkFallback.hue("Радио Песни"),
   RadioStationArtworkFallback.hue("Джаз FM")
  )
 }
}
