package com.musicflow.app

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.Equalizer
import androidx.media3.common.Player
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * Independently implemented native Android equalizer inspired by desktop player
 * features. No Meridius source code or restricted music APIs are imported.
 *
 * Effects attach only to MusicFlow's own ExoPlayer audio session (never session 0).
 * Disabled by default, optional per device, safe to release on session changes.
 */
internal data class AudioEqState(
    val enabled: Boolean = false,
    val preset: String = "flat",
    val gainsDb: List<Float> = List(5) { 0f }
)

internal object AudioEqSettings {
    const val PREFS = "musicflow_eq_v1"
    val labels = listOf("63 Гц", "250 Гц", "1 кГц", "4 кГц", "16 кГц")
    private val centers = listOf(63f, 250f, 1000f, 4000f, 16000f)

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
            gainsDb = gains
        )
    }

    fun save(context: Context, state: AudioEqState) {
        val editor = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean("enabled", state.enabled)
            .putString("preset", state.preset)
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

internal class NativeAudioEqualizer(context: Context) :
    Player.Listener, SharedPreferences.OnSharedPreferenceChangeListener {

    private val application = context.applicationContext
    private val preferences = application.getSharedPreferences(AudioEqSettings.PREFS, Context.MODE_PRIVATE)
    private var audioSessionId = -1
    private var equalizer: Equalizer? = null

    init {
        preferences.registerOnSharedPreferenceChangeListener(this)
    }

    @androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
    override fun onAudioSessionIdChanged(audioSessionId: Int) {
        if (this.audioSessionId == audioSessionId) return
        releaseEffect()
        this.audioSessionId = audioSessionId
        refresh()
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        if (key == "enabled" || key == "preset" || key?.startsWith("gain_") == true) {
            refresh()
        }
    }

    fun refresh() {
        val state = AudioEqSettings.load(application)
        if (!state.enabled || audioSessionId <= 0) {
            releaseEffect()
            return
        }
        try {
            val effect = equalizer ?: Equalizer(0, audioSessionId).also { equalizer = it }
            val range = effect.bandLevelRange
            val count = effect.numberOfBands.toInt()
            for (index in 0 until count) {
                val band = index.toShort()
                val hz = effect.getCenterFreq(band) / 1000f
                val target = AudioEqSettings.gainAtFrequency(state.gainsDb, hz)
                val millibels = (target * 100f).roundToInt()
                    .coerceIn(range[0].toInt(), range[1].toInt())
                effect.setBandLevel(band, millibels.toShort())
            }
            effect.enabled = true
        } catch (failure: Exception) {
            releaseEffect()
            AppDiagnostics.failure("AudioEQ", "Android audio effects unavailable", failure)
        }
    }

    fun release() {
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        releaseEffect()
    }

    private fun releaseEffect() {
        val old = equalizer
        equalizer = null
        runCatching { old?.enabled = false }
        runCatching { old?.release() }
    }
}
