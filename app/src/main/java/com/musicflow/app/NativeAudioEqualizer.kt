package com.musicflow.app

import android.content.Context
import kotlin.math.ln

/**
 * Independently implemented native Android equalizer inspired by desktop player
 * features. No Meridius source code or restricted music APIs are imported.
 *
 * AudioEqSettings are consumed by the in-process PCM processor, not Android's
 * platform Equalizer/LoudnessEnhancer. No AudioFocus or AudioSession mutations.
 */
internal data class AudioEqState(
    val enabled: Boolean = false,
    val preset: String = "flat",
    val gainsDb: List<Float> = List(5) { 0f },
    val boostEnabled: Boolean = false,
    val boostDb: Float = 1f
)

internal object AudioEqSettings {
    const val PREFS = "musicflow_eq_v1"
    val labels = listOf("63 Гц", "250 Гц", "1 кГц", "4 кГц", "16 кГц")
    internal val centers = listOf(63f, 250f, 1000f, 4000f, 16000f)

    val presets = linkedMapOf(
        "flat" to ("Нейтрально" to listOf(0f, 0f, 0f, 0f, 0f)),
        "bass" to ("Басы" to listOf(6f, 4f, 1f, 0f, -1f)),
        "vocal" to ("Вокал" to listOf(-2f, -1f, 3f, 4f, 1f)),
        "rock" to ("Рок" to listOf(3f, 2f, -1f, 2f, 3f)),
        "treble" to ("Высокие" to listOf(-1f, 0f, 1f, 4f, 6f))
    )

    fun load(context: Context): AudioEqState {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val gains = (0 until centers.size).map { index ->
            prefs.getFloat("gain_$index", 0f).let { if (it.isFinite()) it.coerceIn(-10f, 10f) else 0f }
        }
        return AudioEqState(
            enabled = prefs.getBoolean("enabled", false),
            preset = prefs.getString("preset", "flat").orEmpty(),
            gainsDb = gains,
            boostEnabled = prefs.getBoolean("boost_enabled", false),
            boostDb = prefs.getFloat("boost_db", 1f)
                .let { if (it.isFinite()) it.coerceIn(0f, 3f) else 1f }
        )
    }

    fun save(context: Context, state: AudioEqState) {
        val editor = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("enabled", state.enabled)
            .putString("preset", state.preset)
            .putBoolean("boost_enabled", state.boostEnabled)
            .putFloat("boost_db", state.boostDb.coerceIn(0f, 3f))
        state.gainsDb.forEachIndexed { index, gain ->
            editor.putFloat("gain_$index", gain.coerceIn(-10f, 10f))
        }
        editor.apply()
    }

    fun selectPreset(current: AudioEqState, key: String): AudioEqState {
        val preset = presets[key] ?: return current
        return current.copy(preset = key, gainsDb = preset.second)
    }

    /** Interpolate gains logarithmically between controls for device-specific band counts. */
    fun gainAtFrequency(gains: List<Float>, frequencyHz: Float): Float {
        val f = frequencyHz.coerceIn(centers.first(), centers.last())
        val upper = centers.indexOfFirst { it >= f }.coerceAtLeast(0)
        if (upper == 0) return gains[0]
        if (upper >= centers.size) return gains.last()
        val lo = centers[upper - 1]
        val hi = centers[upper]
        val mix = ((ln(f) - ln(lo)) / (ln(hi) - ln(lo))).coerceIn(0f, 1f)
        return gains[upper - 1] * (1f - mix) + gains[upper] * mix
    }
}

