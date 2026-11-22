package com.musicflow.app

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * App-owned PCM 16-bit equalizer. It changes samples, never the player state,
 * audio session, audio focus or Bluetooth output route.
 */
internal class PcmEqEngine {
    private var sampleRate = 0
    private var channelCount = 0
    private var current = AudioEqState()
    private var filters: Array<Array<PeakFilter>> = emptyArray()
    private var gain = 1.0

    fun configure(rate: Int, channels: Int, settings: AudioEqState) {
        require(rate > 0 && channels in 1..8)
        if (rate == sampleRate && channels == channelCount && settings == current) return
        sampleRate = rate
        channelCount = channels
        current = settings
        gain = if (settings.boostEnabled) {
            10.0.pow(settings.boostDb.coerceIn(0f, 3f).toDouble() / 20.0)
        } else 1.0
        filters = if (!settings.enabled) emptyArray() else Array(channels) {
            Array(5) { i ->
                PeakFilter(
                    center = AudioEqSettings.centers[i].toDouble().coerceAtMost(rate * 0.45),
                    db = settings.gainsDb.getOrElse(i) { 0f }.toDouble().coerceIn(-10.0, 10.0),
                    rate = rate.toDouble()
                )
            }
        }
    }

    fun processSample(input: Short, channel: Int): Short {
        if (filters.isEmpty() && gain == 1.0) return input
        val validChannel = channel.coerceIn(0, channelCount - 1)
        var value = input.toDouble()
        if (filters.isNotEmpty()) for (filter in filters[validChannel]) {
            value = filter.apply(value)
        }
        val scaled = (value * gain).roundToInt().coerceIn(-32768, 32767)
        return scaled.toShort()
    }

    fun processPcm(input: ShortArray, channels: Int): ShortArray =
        ShortArray(input.size) { i -> processSample(input[i], i % channels) }

    private class PeakFilter(center: Double, db: Double, rate: Double) {
        private val a1: Double
        private val a2: Double
        private val b0: Double
        private val b1: Double
        private val b2: Double
        private var z1 = 0.0
        private var z2 = 0.0

        init {
            val omega = 2.0 * PI * center / rate
            val cosine = cos(omega)
            val alpha = sin(omega) / 2.0 // Q = 1.0
            val amp = 10.0.pow(db / 40.0)
            val denom = 1.0 + alpha / amp
            b0 = (1.0 + alpha * amp) / denom
            b1 = (-2.0 * cosine) / denom
            b2 = (1.0 - alpha * amp) / denom
            a1 = (-2.0 * cosine) / denom
            a2 = (1.0 - alpha / amp) / denom
        }

        fun apply(sample: Double): Double {
            val output = b0 * sample + z1
            z1 = b1 * sample - a1 * output + z2
            z2 = b2 * sample - a2 * output
            return if (output.isFinite()) output else 0.0
        }
    }
}
