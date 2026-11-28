package com.musicflow.app

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun MusicHttpCacheDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var limit by remember { mutableIntStateOf(MusicHttpCache.limitMiB(context)) }
    var activeLimit by remember { mutableIntStateOf(MusicHttpCache.activeLimitMiB(context)) }
    var bytes by remember { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        try {
            bytes = withContext(Dispatchers.IO) { MusicHttpCache.usedBytes(context) }
        } catch (failure: java.io.IOException) {
            error = "Не удалось определить размер кеша"
        }
    }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Музыкальный кеш") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Занято: " + String.format(Locale.getDefault(), "%.1f МБ", bytes / 1048576.0))
                Text("Повторное воспроизведение использует кеш, если сервер разрешает HTTP-кеширование. Не является скачиванием для офлайн-просмотра.")
                MusicHttpCachePolicy.limitsMiB.forEach { choice ->
                    Row(Modifier.fillMaxWidth()) {
                        RadioButton(selected = choice == limit, enabled = !busy, onClick = {
                            MusicHttpCache.setLimit(context, choice)
                            limit = choice
                            activeLimit = MusicHttpCache.activeLimitMiB(context)
                        })
                        Text(when (choice) {
                            0 -> "Выключен"
                            1024 -> "1 ГБ"
                            2048 -> "2 ГБ"
                            else -> "$choice МБ"
                        }, Modifier.padding(top = 12.dp))
                    }
                }
                if (limit != 0 && limit != activeLimit)
                    Text("Новый лимит вступит в силу после полного перезапуска MusicFlow.",
                        style = MaterialTheme.typography.bodySmall)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("Готово") } },
        dismissButton = {
            TextButton(enabled = !busy, onClick = {
                busy = true
                error = null
                scope.launch {
                    try {
                        bytes = withContext(Dispatchers.IO) {
                            MusicHttpCache.clear(context)
                            MusicHttpCache.usedBytes(context)
                        }
                    } catch (failure: Exception) {
                        if (failure is kotlinx.coroutines.CancellationException) throw failure
                        error = "Не удалось очистить кеш"
                    } finally { busy = false }
                }
            }) { Text(if (busy) "Очистка…" else "Очистить кеш") }
        }
    )
}
