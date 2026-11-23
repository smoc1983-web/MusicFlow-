package com.musicflow.app
import org.junit.Assert.*
import org.junit.Test
class RadioStationArtworkFallbackTest {
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
}