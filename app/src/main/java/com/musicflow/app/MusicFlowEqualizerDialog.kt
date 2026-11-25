package com.musicflow.app

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Five user-friendly bands are processed directly in the MusicFlow PCM output.
 * The same saved settings now affect music, radio, TV and the cinema player.
 */
@Composable
internal fun MusicFlowEqualizerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalMusicFlowPalette.current
    var state by remember { mutableStateOf(AudioEqSettings.load(context)) }

    fun update(next: AudioEqState) {
        state = next
        AudioEqSettings.save(context, next)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Звук · эквалайзер", color = palette.foreground) },
        containerColor = palette.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Эквалайзер", fontWeight = FontWeight.SemiBold)
                        Text("Выключен по умолчанию", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = state.enabled,
                        onCheckedChange = { checked -> update(state.copy(enabled = checked)) }
                    )
                }
                Text("Пресеты", fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AudioEqSettings.presets.forEach { (key, preset) ->
                        FilterChip(
                            selected = state.preset == key,
                            onClick = { update(AudioEqSettings.selectPreset(state, key)) },
                            label = { Text(preset.first) }
                        )
                    }
                }
                AudioEqSettings.labels.forEachIndexed { index, label ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(label, modifier = Modifier.width(68.dp),
                            style = MaterialTheme.typography.labelMedium)
                        Slider(
                            modifier = Modifier.weight(1f),
                            value = state.gainsDb[index],
                            onValueChange = { value ->
                                val gains = state.gainsDb.toMutableList()
                                gains[index] = (value * 2f).roundToInt() / 2f
                                update(state.copy(preset = "custom", gainsDb = gains))
                            },
                            enabled = state.enabled,
                            valueRange = -10f..10f
                        )
                        val gain = state.gainsDb[index]
                        Text(
                            (if (gain > 0f) "+" else "") + gain.toString(),
                            modifier = Modifier.width(48.dp),
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                HorizontalDivider()
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Усиление тихого звука", fontWeight = FontWeight.SemiBold)
                        Text("Не автоматическая нормализация",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = state.boostEnabled,
                        onCheckedChange = { update(state.copy(boostEnabled = it)) }
                    )
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Slider(
                        modifier = Modifier.weight(1f),
                        value = state.boostDb,
                        onValueChange = { value ->
                            update(state.copy(boostDb = (value * 2f).roundToInt() / 2f))
                        },
                        enabled = state.boostEnabled,
                        valueRange = 0f..3f
                    )
                    Text("+" + state.boostDb.toString() + " дБ",
                        modifier = Modifier.width(72.dp),
                        style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    "Настройки действуют в плеерах музыки, радио, ТВ и кино. " +
                        "При прослушивании в наушниках используйте умеренную громкость.",
                    style = MaterialTheme.typography.bodySmall, color = palette.muted
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Готово", color = palette.accent) }
        },
        dismissButton = {
            TextButton(onClick = { update(AudioEqState()) }) {
                Text("Сбросить", color = palette.accent)
            }
        }
    )
}
