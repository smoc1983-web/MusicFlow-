package com.musicflow.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
internal fun DiagnosticsScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var display by remember { mutableStateOf("") }
    var note by remember { mutableStateOf<String?>(null) }
    var showSourceChecks by remember { mutableStateOf(false) }
    fun refresh() { display = AppDiagnostics.recentLines() }
    LaunchedEffect(Unit) { refresh() }
    if (showSourceChecks) MusicSourceDiagnosticsDialog(
        onClose = { showSourceChecks = false; refresh() }, onResult = { refresh() }
    )
    Column(modifier.padding(top = 10.dp)) {
        Text("Журнал диагностики", style = MaterialTheme.typography.titleLarge)
        Button(onClick = { showSourceChecks = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Проверить источники музыки")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            "Файл хранится только на телефоне (не более 512 КБ). " +
                "В нём ошибки поиска, IPTV и плеера. Отправка только вручную. " +
                "Перед отправкой проверь содержимое.",
            style = MaterialTheme.typography.bodySmall,
            color = Color.LightGray
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            TextButton(onClick = { refresh(); note = null }) { Text("Обновить") }
            TextButton(onClick = { note = AppDiagnostics.share(context) }) {
                Text("Отправить файл")
            }
            TextButton(onClick = {
                AppDiagnostics.clear()
                refresh()
                note = "Журнал очищен"
            }) { Text("Очистить") }
        }
        note?.let {
            Text(it, style = MaterialTheme.typography.bodySmall,
                 color = Color(0xFFFFC6BD))
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.weight(1f)) {
            SelectionContainer {
                Text(
                    text = display.ifBlank { "Записей пока нет" },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFDAD5E5),
                    modifier = Modifier.fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(4.dp)
                )
            }
        }
    }
}
