package com.musicflow.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Simple, private, on-device music insights: time verified by Media3 service. */
@Composable
internal fun ListeningInsightsPanel(
    records: List<TrackListeningSummary>,
    modifier: Modifier = Modifier
) {
    val palette = LocalMusicFlowPalette.current
    val totalMs = records.sumOf { it.listenedMs }
    val totalMinutes = totalMs / 60_000L
    val totalPlays = records.sumOf { it.playCount }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(9.dp)) {
        Text("Статистика прослушивания", style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold, color = palette.foreground)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricCard("${totalMinutes / 60} ч ${totalMinutes % 60} мин",
                "Прослушано", Modifier.weight(1f))
            MetricCard(totalPlays.toString(), "Запусков", Modifier.weight(1f))
            MetricCard(records.size.toString(), "Записей", Modifier.weight(1f))
        }
        Text("Время учитывается при реальном воспроизведении. Паузы и перемотка " +
            "не добавляют минуты. Данные сохраняются только на телефоне.",
            style = MaterialTheme.typography.bodySmall, color = palette.muted)
        if (records.isEmpty()) {
            Text("Статистика появится после прослушивания музыки или радио.",
                color = palette.muted)
        } else {
            Text("Самое прослушиваемое", style = MaterialTheme.typography.titleMedium)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                itemsIndexed(records.take(50), key = { _, item -> item.id }) { index, item ->
                    Surface(shape = RoundedCornerShape(12.dp), color = palette.surface) {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 11.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${index + 1}.", color = palette.accent, modifier = Modifier.width(28.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.title, color = palette.foreground, maxLines = 1,
                                    fontWeight = FontWeight.SemiBold,
                                    overflow = TextOverflow.Ellipsis)
                                Text(item.artist, color = palette.muted, maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                val minutes = (item.listenedMs / 60_000L).coerceAtLeast(0L)
                                Text("${minutes} мин", color = palette.foreground,
                                    style = MaterialTheme.typography.labelMedium)
                                Text("${item.playCount} запусков", color = palette.muted,
                                    style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricCard(value: String, label: String, modifier: Modifier) {
    val palette = LocalMusicFlowPalette.current
    Surface(shape = RoundedCornerShape(12.dp), color = palette.surface,
        modifier = modifier) {
        Column(Modifier.padding(9.dp)) {
            Text(value, fontWeight = FontWeight.Bold, color = palette.accent,
                style = MaterialTheme.typography.titleSmall)
            Text(label, color = palette.muted, style = MaterialTheme.typography.labelSmall)
        }
    }
}
