package com.musicflow.app

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Check public signed releases quietly on app startup. Present new updates
 * inside MusicFlow instead of sending the user to a browser.
 *
 * Installation is confirmed in Android's normal package installer. A release
 * must use the same package name and signing key as the installed application.
 */
@Composable
internal fun MusicFlowAutoUpdate() {
    val context = LocalContext.current
    val palette = LocalMusicFlowPalette.current
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf<MusicFlowUpdate?>(null) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        val preferences = context.getSharedPreferences("musicflow_updates", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        // Avoid repetitive API requests while reopening the UI or rotating.
        val lastCheck = preferences.getLong("last_check", 0L)
        if (lastCheck > 0 && now - lastCheck in 0L until 6L * 60L * 60L * 1000L) return@LaunchedEffect
        try {
            val latest = MusicFlowUpdates.latestRelease()
            preferences.edit().putLong("last_check", now).apply()
            if (latest != null && latest.versionCode > MusicFlowUpdates.currentCode(context)) {
                val snoozedVersion = preferences.getLong("snoozed_version", 0L)
                val snoozedUntil = preferences.getLong("snoozed_until", 0L)
                if (latest.versionCode != snoozedVersion || now >= snoozedUntil) {
                    available = latest
                }
            }
        } catch (_: Exception) {
            // The app works normally when offline or GitHub is unreachable.
        }
    }

    val update = available
    if (update != null) {
        AlertDialog(
            onDismissRequest = { if (!busy) available = null },
            containerColor = palette.surface,
            title = { Text("Доступно обновление MusicFlow", color = palette.foreground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Новая версия: " + update.version,
                        fontWeight = FontWeight.SemiBold, color = palette.accent)
                    Text("Установленная версия: " + MusicFlowUpdates.currentVersion(context),
                        color = palette.muted)
                    Text("Размер: " + (update.bytes / 1_048_576) + " МБ",
                        color = palette.muted)
                    if (update.notes.isNotBlank()) {
                        Text(update.notes.take(250), color = palette.foreground,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (busy) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = palette.accent
                        )
                        Text("Загрузка: " + progress + "%",
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.foreground)
                    }
                    if (error != null) {
                        Text(error.orEmpty(), color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !busy,
                    onClick = {
                        if (!MusicFlowUpdates.canInstall(context)) {
                            error = "Разрешите MusicFlow установку приложений в настройках Android и повторите установку."
                            runCatching {
                                context.startActivity(MusicFlowUpdates.allowInstallIntent(context))
                            }
                        } else {
                            busy = true
                            error = null
                            progress = 0
                            scope.launch {
                                try {
                                    val uri = MusicFlowUpdates.downloadVerified(context, update) { percent ->
                                        withContext(Dispatchers.Main) { progress = percent }
                                    }
                                    busy = false
                                    context.startActivity(MusicFlowUpdates.installerIntent(uri))
                                    available = null
                                } catch (failure: Exception) {
                                    error = "Не удалось установить обновление: " +
                                        (failure.message ?: "ошибка загрузки")
                                    busy = false
                                }
                            }
                        }
                    }
                ) { Text(if (busy) "Загружается…" else "Скачать и установить") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = {
                    context.getSharedPreferences("musicflow_updates", Context.MODE_PRIVATE)
                        .edit()
                        .putLong("snoozed_version", update.versionCode)
                        .putLong("snoozed_until", System.currentTimeMillis() + 24L * 60L * 60L * 1000L)
                        .apply()
                    available = null
                }) { Text("Позже", color = palette.accent) }
            }
        )
    }
}
