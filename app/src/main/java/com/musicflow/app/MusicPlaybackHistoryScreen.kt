package com.musicflow.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun MusicPlaybackHistoryScreen(
    activeId: String,
    onPlay: (Track) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val palette = LocalMusicFlowPalette.current
    var entries by remember { mutableStateOf(PlaybackJournal.history(context)) }
    var confirmErase by remember { mutableStateOf(false) }
    LaunchedEffect(activeId) {
        entries = PlaybackJournal.history(context)
    }
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.History, contentDescription = null, tint = palette.accent)
            Spacer(Modifier.width(9.dp))
            Text("История прослушивания", fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f))
            IconButton(onClick = { confirmErase = true }, enabled = entries.isNotEmpty()) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Очистить историю",
                    tint = palette.muted)
            }
        }
        Text("Хранится только на устройстве. Нажмите на запись, чтобы прослушать её.",
            color = palette.muted, style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(10.dp))
        if (entries.isEmpty()) {
            Text("Пока нет прослушанных композиций", color = palette.muted)
        }
        LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(entries, key = { it.id }) { track ->
                Surface(color = palette.surface, shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().clickable { onPlay(track) }) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "Воспроизвести",
                            tint = palette.accent)
                        Spacer(Modifier.width(9.dp))
                        Column(Modifier.weight(1f)) {
                            Text(track.title, maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontWeight = if (track.id == activeId) FontWeight.Bold else FontWeight.Normal)
                            Text(track.artist, maxLines = 1,
                                overflow = TextOverflow.Ellipsis, color = palette.muted,
                                style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
    if (confirmErase) AlertDialog(
        onDismissRequest = { confirmErase = false },
        title = { Text("Очистить историю?") },
        text = { Text("Это удалит только историю, но не избранное и очередь.") },
        confirmButton = {
            TextButton(onClick = {
                PlaybackJournal.clearHistory(context)
                entries = emptyList()
                confirmErase = false
            }) { Text("Очистить") }
        },
        dismissButton = {
            TextButton(onClick = { confirmErase = false }) { Text("Отмена") }
        }
    )
}
