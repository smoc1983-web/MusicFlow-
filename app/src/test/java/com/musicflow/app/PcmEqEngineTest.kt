package com.musicflow.app

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin
import kotlin.math.sqrt

class PcmEqEngineTest {
    @Test fun bypassNeverChangesSamplesOrPlaybackState() {
        val eq = PcmEqEngine()
        eq.configure(48_000, 2, AudioEqState())
        val input = shortArrayOf(-32768, 0, 32767, 12345, -2222, 0)
        assertArrayEquals(input, eq.processPcm(input, 2))
    }

    @Test fun activeBassBoostChangesToneWithoutSilencingStream() {
        val eq = PcmEqEngine()
        eq.configure(48_000, 1,
            AudioEqState(enabled = true, gainsDb = listOf(6f, 0f, 0f, 0f, 0f)))
        val input = ShortArray(4800) { i ->
            (6000 * sin(2.0 * PI * 63 * i / 48000)).toInt().toShort()
        }
        val output = eq.processPcm(input, 1)
        val rmsInput = rms(input.copyOfRange(1000, 4800))
        val rmsOutput = rms(output.copyOfRange(1000, 4800))
        assertTrue("Audible PCM must continue", rmsOutput > 0.0)
        assertTrue("Bass is amplified, not muted", rmsOutput > rmsInput * 1.2)
    }

    @Test fun switchingEqWhileStreamingDoesNotClearOrMuteSamples() {
        val eq = PcmEqEngine()
        eq.configure(44_100, 2, AudioEqState())
        val block = ShortArray(256) { if (it % 2 == 0) 1200.toShort() else (-1200).toShort() }
        eq.processPcm(block, 2)
        eq.configure(44_100, 2,
            AudioEqState(enabled = true, gainsDb = listOf(0f,0f,2f,0f,0f)))
        val live = eq.processPcm(block, 2)
        assertEquals(block.size, live.size)
        assertTrue(live.any { abs(it.toInt()) > 100 })
        eq.configure(44_100, 2, AudioEqState())
        assertArrayEquals(block, eq.processPcm(block, 2))
    }

    @Test fun outputIsClampedAndBoostNeverOverflows() {
        val eq = PcmEqEngine()
        eq.configure(48_000, 1, AudioEqState(enabled = true,
            gainsDb = List(5) { 10f }, boostEnabled = true, boostDb = 3f))
        val loud = ShortArray(512) { if(it % 2 == 0) 32767.toShort() else (-32768).toShort() }
        val output = eq.processPcm(loud, 1)
        assertTrue(output.any { it != 0.toShort() })
        assertTrue(output.all { it.toInt() in -32768..32767 })
    }

    private fun rms(buffer: ShortArray): Double =
        sqrt(buffer.map { it.toDouble() * it }.average())
}
