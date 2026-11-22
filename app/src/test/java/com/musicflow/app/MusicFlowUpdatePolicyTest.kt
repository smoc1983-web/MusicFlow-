package com.musicflow.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prevent repeated dialogs after a successful in-place update. */
class MusicFlowUpdatePolicyTest {
    @Test fun installedReleaseIsNeverOfferedAgain() {
        assertFalse(MusicFlowUpdatePolicy.isNewer(743, 743))
        assertFalse(MusicFlowUpdatePolicy.isNewer(742, 743))
        assertFalse(MusicFlowUpdatePolicy.shouldShow(743, 743, false))
        assertFalse(MusicFlowUpdatePolicy.shouldShow(null, 743, false))
    }

    @Test fun onlyHigherVersionIsOfferedUnlessHidden() {
        assertTrue(MusicFlowUpdatePolicy.isNewer(744, 743))
        assertTrue(MusicFlowUpdatePolicy.shouldShow(744, 743, false))
        assertFalse(MusicFlowUpdatePolicy.shouldShow(744, 743, true))
        assertTrue(MusicFlowUpdatePolicy.shouldShow(745, 744, false))
    }
}
