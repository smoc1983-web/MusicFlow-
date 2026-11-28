package com.musicflow.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
internal fun MusicSourceDiagnosticsDialog(onClose: () -> Unit, onResult: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("Queen") }
    val results = remember { mutableStateMapOf<String, String>() }
    var running by remember { mutableStateOf(false) }
    var job by remember { mutableStateOf<Job?>(null) }
    fun run(ids: List<String>) {
        if (running || query.isBlank()) return
        val requested = query.trim()
        running = true
        job = scope.launch {
            try {
                for (id in ids) {
                    val result = probeMusicSource(context, id, requested) { results[id] = it }
                    results[id] = result
                    AppDiagnostics.info("SourceCheck", "source=$id $result")
                    onResult()
                }
            } finally { running = false }
        }
    }
    AlertDialog(
        onDismissRequest = { job?.cancel(); onClose() },
        title = { Text("Проверка источников музыки") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Поиск и проверка реального аудио отдельно, без переключения на другой источник. Звук проверки отключён.")
                OutlinedTextField(value = query, onValueChange = { query = it },
                    label = { Text("Запрос") }, singleLine = true, enabled = !running)
                Button(onClick = { run(diagnosticMusicSources) },
                    enabled = !running && query.isNotBlank(), modifier = Modifier.fillMaxWidth()) {
                    Text("Проверить все источники")
                }
                for (id in diagnosticMusicSources) {
                    HorizontalDivider()
                    Text(diagnosticSourceLabel(id), style = MaterialTheme.typography.titleSmall)
                    Text(results[id] ?: "Не проверен", style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = { run(listOf(id)) }, enabled = !running && query.isNotBlank()) {
                        Text("Проверить " + diagnosticSourceLabel(id))
                    }
                }
                if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { job?.cancel(); onClose() }) { Text("Закрыть") }
        },
        dismissButton = {
            if (running) TextButton(onClick = {
                job?.cancel()
                results.keys.toList().forEach { id ->
                    if (results[id]?.endsWith("…") == true) results[id] = "Проверка отменена"
                }
            }) { Text("Отменить") }
        }
    )
}
