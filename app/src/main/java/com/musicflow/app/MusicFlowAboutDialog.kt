package com.musicflow.app

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun MusicFlowAboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val palette = LocalMusicFlowPalette.current
    val scope = rememberCoroutineScope()
    var update by remember { mutableStateOf<MusicFlowUpdate?>(null) }
    var checking by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("Обновления проверяются по официальным выпускам GitHub.") }
    val currentVersion = remember { MusicFlowUpdates.currentVersion(context) }

    fun openLink(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            .onFailure { message = "Невозможно открыть ссылку: " + (it.message ?: "") }
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        containerColor = palette.surface,
        title = { Text("О приложении MusicFlow", color = palette.foreground) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                Text("Версия " + currentVersion,
                    color = palette.accent,
                    style = MaterialTheme.typography.labelLarge)
                Text(message, color = palette.muted,
                    style = MaterialTheme.typography.bodySmall)
                if (checking || downloading) {
                    if (downloading) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = palette.accent
                        )
                        Text("Загружено " + progress + "%",
                            color = palette.foreground,
                            style = MaterialTheme.typography.labelSmall)
                    } else LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(), color = palette.accent
                    )
                }
                Button(
                    enabled = !checking && !downloading,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        checking = true
                        update = null
                        message = "Проверяем выпуски MusicFlow…"
                        scope.launch {
                            try {
                                val candidate = MusicFlowUpdates.latestRelease()
                                if (candidate == null) {
                                    message = "Подписанные APK-выпуски ещё не опубликованы."
                                } else if (candidate.versionCode > MusicFlowUpdates.currentCode(context)) {
                                    update = candidate
                                    message = "Доступна версия " + candidate.version +
                                        ". Размер: " + (candidate.bytes / 1_048_576) + " МБ."
                                } else {
                                    message = "Установлена актуальная версия."
                                }
                            } catch (error: Exception) {
                                message = "Проверка не выполнена: " +
                                    (error.message ?: "нет подключения")
                            } finally {
                                checking = false
                            }
                        }
                    }
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Проверить обновления")
                }
                update?.let { release ->
                    Button(
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !downloading && !checking,
                        onClick = {
                            if (!MusicFlowUpdates.canInstall(context)) {
                                message = "Разрешите установку APK для MusicFlow в настройках Android, " +
                                    "затем нажмите «Установить» повторно."
                                runCatching {
                                    context.startActivity(MusicFlowUpdates.allowInstallIntent(context))
                                }
                            } else {
                                downloading = true
                                progress = 0
                                message = "Скачивание официального APK…"
                                scope.launch {
                                    try {
                                        val apk = MusicFlowUpdates.downloadVerified(context, release) { percent ->
                                            withContext(Dispatchers.Main) { progress = percent }
                                        }
                                        downloading = false
                                        message = "Подтвердите установку в системном окне Android."
                                        context.startActivity(MusicFlowUpdates.installerIntent(apk))
                                    } catch (error: Exception) {
                                        message = "Обновление не установлено: " +
                                            (error.message ?: "ошибка загрузки")
                                    } finally {
                                        downloading = false
                                    }
                                }
                            }
                        }
                    ) { Text("Скачать и установить " + release.version) }
                }
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openLink(MusicFlowUpdates.RELEASES_PAGE) }
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("История выпусков")
                }
                HorizontalDivider(color = palette.accent.copy(alpha = 0.18f))
                Text("Поддержать разработчика", fontWeight = FontWeight.SemiBold,
                    color = palette.foreground)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                            as ClipboardManager
                        clipboard.setPrimaryClip(
                            ClipData.newPlainText("Телефон для перевода",
                                MusicFlowUpdates.SUPPORT_NUMBER)
                        )
                        message = "Номер " + MusicFlowUpdates.SUPPORT_NUMBER +
                            " скопирован. Перевод можно сделать через приложение вашего банка."
                    }
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Скопировать +7 912 858-06-80")
                }
                Text("Перевод по номеру телефона через ваш банк.",
                    color = palette.muted,
                    style = MaterialTheme.typography.labelSmall)
                OutlinedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { openLink(MusicFlowUpdates.TELEGRAM_URL) }
                ) {
                    Icon(Icons.Filled.OpenInNew, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Telegram-канал MusicFlow")
                }
                Text("Ссылка откроется для участников канала.",
                    color = palette.muted,
                    style = MaterialTheme.typography.labelSmall)
            }
        },
        confirmButton = {
            TextButton(
                enabled = !downloading,
                onClick = onDismiss
            ) { Text("Закрыть", color = palette.accent) }
        }
    )
}
