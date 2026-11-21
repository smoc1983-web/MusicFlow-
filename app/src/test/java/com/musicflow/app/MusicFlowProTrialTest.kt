package com.musicflow.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicFlowProTrialTest {
    private val payload = "musicflow-pro-test:v1:enable_download"
    private val signature =
        "F7USqBo/LSQfMbvZXzrz7LvU/krwy0Icn4tc/8zArpx/XRCnjmsldBSMpSczUnp1FhRcGG4ZaooSz4wi1qI7+rgBDu/goYBJgUoCrNUmL37iP87t5bix6ROx88UFdvOknFaKZa0pQfwI7AT+5703ZE82bHsTsSsjUSHVXdr/mWImvFVdVB4yYZPvBwyoBUkAhH3wyjXfPmwLjaxLqMQJaqo1DJJFMB6wP6YBaoaZwK7dJ+VygNw6Gdos4CR1NVLnG0P6Jblr56HzXUIYP6mumkQsgKnJp5NrYNEG9/gKlbpM3uAPf+E1Uq08EzvyP4UWQO7b4tXo4juZkZ4aCQ3WBQ=="

    @Test fun noPaymentNoAccess() {
        assertFalse(MusicFlowProTrial.isUnlocked(false, false))
        assertFalse(MusicFlowProTrial.isUnlocked(false, true))
        assertFalse(MusicFlowProTrial.isUnlocked(true, false))
        assertTrue(MusicFlowProTrial.isUnlocked(true, true))
    }

    @Test fun authenticGitHubPatchAccepted() {
        assertTrue(MusicFlowProTrial.verifySignedPatch(payload, signature))
    }

    @Test fun modifiedPatchRejected() {
        assertFalse(MusicFlowProTrial.verifySignedPatch(payload + "-tampered", signature))
        assertFalse(MusicFlowProTrial.verifySignedPatch(payload, signature.reversed()))
        assertFalse(MusicFlowProTrial.verifySignedPatch(payload, "invalid!"))
    }

    @Test fun invalidOrMissingWavRejected() {
        assertFalse(MusicFlowProTrial.isValidFixtureWav(ByteArray(0)))
        assertFalse(MusicFlowProTrial.isValidFixtureWav(ByteArray(64)))
        assertFalse(MusicFlowProTrial.isValidFixtureWav(ByteArray(32_001)))
    }

    @Test fun simpleTestWavAccepted() {
        val wav = ByteArray(60)
        "RIFF".toByteArray().copyInto(wav, 0)
        "WAVE".toByteArray().copyInto(wav, 8)
        "data".toByteArray().copyInto(wav, 36)
        assertTrue(MusicFlowProTrial.isValidFixtureWav(wav))
        wav[0] = 0
        assertFalse(MusicFlowProTrial.isValidFixtureWav(wav))
    }
}
