package com.musicflow.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** A simple sleep timer; it never requires alarms, wake locks or background permissions. */
@Composable
internal fun MusicFlowSleepTimerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    var deadlineMs by remember { mutableStateOf(MusicFlowSleepTimerStore.deadlineMs(context)) }
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(1_000L)
            nowMs = System.currentTimeMillis()
            deadlineMs = MusicFlowSleepTimerStore.deadlineMs(context)
        }
    }

    val remaining = SleepTimerPolicy.remainingMs(deadlineMs, nowMs)
    val time = if (remaining > 0L) {
        val minutes = remaining / 60_000L
        val seconds = (remaining / 1_000L) % 60
        "Осталось %d:%02d".format(minutes, seconds)
    } else "Таймер не включён"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Таймер сна") },
        shape = RoundedCornerShape(24.dp),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(time, style = MaterialTheme.typography.titleMedium)
                Text("Музыка и радио остановятся по окончании времени. " +
                    "Очередь и прогресс прослушивания сохраняются.",
                    style = MaterialTheme.typography.bodySmall)
                listOf(10, 15, 20, 30, 45, 60).chunked(3).forEach { row ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        row.forEach { minutes ->
                            OutlinedButton(
                                onClick = {
                                    deadlineMs = MusicFlowSleepTimerStore.schedule(context, minutes)
                                    nowMs = System.currentTimeMillis()
                                },
                                modifier = Modifier.weight(1f)
                            ) { Text("$minutes мин") }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
        dismissButton = {
            TextButton(
                onClick = {
                    MusicFlowSleepTimerStore.cancel(context)
                    deadlineMs = null
                },
                enabled = deadlineMs != null
            ) { Text("Выключить") }
        }
    )
}
