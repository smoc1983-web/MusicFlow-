package com.musicflow.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Native source manager. Sources appear as tabs inside Cinema and use Media3.
 * A legacy Lampa JS URL is kept as a reference, never silently executed.
 */
@Composable
internal fun CinemaNativeSourcesDialog(
    sources: List<CinemaNativeSource>,
    busy: Boolean,
    error: String?,
    onClose: () -> Unit,
    onAdd: (String, String) -> Unit,
    onRemove: (CinemaNativeSource) -> Unit
) {
    var address by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = { if (!busy) onClose() },
        title = { Text("Источники кино · Media3") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text("Добавьте источник по ссылке. Поддерживаются JSON-каталог, " +
                    "M3U-плейлист или прямой HTTPS-поток MP4/HLS. " +
                    "Видео запускается в плеере MusicFlow, а не в Lampa.",
                    style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(64) },
                    label = { Text("Название источника (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it.take(2048) },
                    label = { Text("URL источника") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { address = CinemaNativeSources.DEMO_M3U },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("M3U демо") }
                    OutlinedButton(
                        onClick = { address = CinemaNativeSources.DEMO_JSON },
                        enabled = !busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("JSON демо") }
                }
                Button(
                    onClick = { onAdd(name.trim(), address.trim()) },
                    enabled = !busy && address.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (busy) "Проверяем источник…" else "Добавить в каталог")
                }
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }
                if (sources.isNotEmpty()) {
                    HorizontalDivider()
                    Text("Подключено", style = MaterialTheme.typography.titleSmall)
                    sources.forEach { source ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment =
                            androidx.compose.ui.Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(source.name, style = MaterialTheme.typography.bodySmall)
                                Text(when(source.format) {
                                    CinemaSourceFormat.CINEMA_JSON -> "JSON-каталог"
                                    CinemaSourceFormat.M3U_PLAYLIST -> "M3U → Media3"
                                    CinemaSourceFormat.DIRECT_MEDIA -> "Видео → Media3"
                                    CinemaSourceFormat.LAMPA_REFERENCE ->
                                        "Lampa JS · нужен адаптер, видео недоступно"
                                }, style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { onRemove(source) }, enabled = !busy) {
                                Text("Удалить")
                            }
                        }
                    }
                }
                HorizontalDivider()
                Text("Плагины Lampa на JavaScript можно сохранить как ссылки, " +
                    "но их собственный API не возвращает универсальный список " +
                    "видеопотоков для Media3. Для каждого такого плагина требуется " +
                    "нативный адаптер и разрешённый источник видео. " +
                    "Добавление ссылки само по себе не означает, что видео заработает.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onClose, enabled = !busy) { Text("Готово") }
        }
    )
}
