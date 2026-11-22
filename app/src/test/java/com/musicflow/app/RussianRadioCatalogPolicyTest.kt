package com.musicflow.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Regression: station catalog must not depend on the directory's regional health status. */
class RussianRadioCatalogPolicyTest {
    @Test fun russianStationAppearsEvenWhenThirdPartyHealthFlagIsUnreliable() {
        assertTrue(RussianRadioDirectory.isListingCandidate("RU", "uuid-123", "Радио"))
        assertTrue(RussianRadioDirectory.isListingCandidate("ru", "uuid-456", "Русское радио"))
    }

    @Test fun invalidOrForeignEntriesAreExcluded() {
        assertFalse(RussianRadioDirectory.isListingCandidate("US", "uuid-123", "Радио"))
        assertFalse(RussianRadioDirectory.isListingCandidate("RU", "", "Радио"))
        assertFalse(RussianRadioDirectory.isListingCandidate("RU", "uuid-123", ""))
    }
}
