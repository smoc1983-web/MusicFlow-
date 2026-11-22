package com.musicflow.app

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Automatic update discovery and installer UI. Android DownloadManager owns
 * the APK transfer, independently of this dialog and this Activity.
 */
/** Never offer an APK with the same or older versionCode. */
internal object MusicFlowUpdatePolicy {
    fun isNewer(candidateCode: Long, installedCode: Long): Boolean =
        candidateCode > installedCode

    fun shouldShow(candidateCode: Long?, installedCode: Long, hidden: Boolean): Boolean =
        !hidden && candidateCode != null && isNewer(candidateCode, installedCode)
}

@Composable
internal fun MusicFlowAutoUpdate() {
    val context = LocalContext.current
    val palette = LocalMusicFlowPalette.current
    val announcementPreferences = remember(context) {
        context.getSharedPreferences("musicflow_updates", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    var available by remember { mutableStateOf<MusicFlowUpdate?>(null) }
    var working by remember { mutableStateOf(false) }
    var hidden by remember { mutableStateOf(false) }
    fun candidateAllowed(candidate: MusicFlowUpdate?): Boolean =
        UpdateDialogGate.visible(UpdateDialogGate.State(
            installedCode = MusicFlowUpdates.currentCode(context),
            candidateCode = candidate?.versionCode,
            ignoredCode = announcementPreferences.getLong("ignored_code", 0L),
            dismissed = hidden
        ))
    var transfer by remember {
        mutableStateOf(MusicFlowBackgroundUpdates.Snapshot(
            MusicFlowBackgroundUpdates.Phase.IDLE))
    }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!MusicFlowUpdates.supportsOfficialUpdates(context)) return@LaunchedEffect
        val installed = MusicFlowUpdates.currentCode(context)
        val saved = MusicFlowBackgroundUpdates.savedRelease(context)
            ?.takeIf { MusicFlowUpdatePolicy.isNewer(it.versionCode, installed) }
        if (candidateAllowed(saved)) available = saved
        val preferences = context.getSharedPreferences("musicflow_updates", Context.MODE_PRIVATE)
        val now = System.currentTimeMillis()
        try {
            val latest = MusicFlowUpdates.latestRelease()
            if (candidateAllowed(latest)) {
                val snoozedVersion = preferences.getLong("snoozed_version", 0L)
                val snoozedUntil = preferences.getLong("snoozed_until", 0L)
                if (saved != null || latest.versionCode != snoozedVersion || now >= snoozedUntil) {
                    available = latest
                }
            }
        } catch (failure: Exception) {
            AppDiagnostics.failure("Updates", "automatic update check", failure)
        }
    }

    LaunchedEffect(Unit) {
        if (!MusicFlowUpdates.supportsOfficialUpdates(context)) return@LaunchedEffect
        while (true) {
            try {
                transfer = MusicFlowBackgroundUpdates.snapshot(context)
                val saved = MusicFlowBackgroundUpdates.savedRelease(context)
                if (saved != null && available == null &&
                    candidateAllowed(saved) &&
                    transfer.phase != MusicFlowBackgroundUpdates.Phase.IDLE) {
                    available = saved
                }
            } catch (failure: Exception) {
                AppDiagnostics.failure("Updates", "background download status", failure)
            }
            delay(1000L)
        }
    }

    val update = available
    if (update != null && candidateAllowed(update)) {
        val sameVersion = update.versionCode ==
            MusicFlowBackgroundUpdates.savedRelease(context)?.versionCode
        val loading = sameVersion && transfer.phase in setOf(
            MusicFlowBackgroundUpdates.Phase.WAITING,
            MusicFlowBackgroundUpdates.Phase.RUNNING
        )
        val downloaded = sameVersion &&
            transfer.phase == MusicFlowBackgroundUpdates.Phase.DOWNLOADED
        val progress = if (sameVersion) transfer.progress else 0
        val transferError = if (sameVersion &&
            transfer.phase == MusicFlowBackgroundUpdates.Phase.FAILED) transfer.error else null

        AlertDialog(
            onDismissRequest = {
                if (!working) { hidden = true; available = null }
            },
            containerColor = palette.surface,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
            title = { Text("Доступно обновление MusicFlow", color = palette.foreground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Новая версия: " + update.version.removePrefix("v"),
                        fontWeight = FontWeight.SemiBold, color = palette.accent)
                    Text("Установленная версия: " + MusicFlowUpdates.currentVersion(context),
                        color = palette.muted)
                    Text("Размер: " + (update.bytes / 1_048_576) + " МБ",
                        color = palette.muted)
                    if (update.notes.isNotBlank()) {
                        Text(update.notes.take(250), color = palette.foreground,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (loading) {
                        LinearProgressIndicator(progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(), color = palette.accent)
                        Text("Скачивание: $progress% · можно свернуть приложение",
                            color = palette.foreground,
                            style = MaterialTheme.typography.bodySmall)
                    }
                    if (downloaded) {
                        Text("APK скачан в фоне. Подпись будет проверена перед установкой.",
                            color = palette.accent, style = MaterialTheme.typography.bodySmall)
                    }
                    val shownError = error ?: transferError
                    if (shownError != null) {
                        Text(shownError, color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !working && !loading,
                    onClick = {
                        // Dismiss in this click handler, BEFORE suspension/network calls.
                        // This guarantees the modal is gone even when DownloadManager stalls.
                        val nextState = UpdateDialogGate.onConfirm(UpdateDialogGate.State(
                            MusicFlowUpdates.currentCode(context), update.versionCode,
                            announcementPreferences.getLong("ignored_code", 0L), hidden
                        ))
                        hidden = nextState.dismissed
                        available = null
                        announcementPreferences.edit()
                            .putLong("ignored_code", nextState.ignoredCode).apply()
                        working = true
                        error = null
                        scope.launch {
                            try {
                                if (downloaded) {
                                    // This reuses the existing package/version/signature checks.
                                    val uri = MusicFlowBackgroundUpdates.verifiedInstallUri(context)
                                    if (!MusicFlowUpdates.canInstall(context)) {
                                        error = "Разрешите установку APK для MusicFlow и нажмите «Установить» снова."
                                        context.startActivity(MusicFlowUpdates.allowInstallIntent(context))
                                    } else {
                                        context.startActivity(MusicFlowUpdates.installerIntent(uri))
                                    }
                                } else {
                                    MusicFlowBackgroundUpdates.enqueue(context, update)
                                    transfer = MusicFlowBackgroundUpdates.snapshot(context)
                                    // The announcement was dismissed synchronously on tap.
                                    // DownloadManager continues even if this Activity exits.
                                }
                            } catch (failure: Exception) {
                                val message = "Не удалось подготовить обновление: " +
                                    (failure.message ?: "ошибка загрузки")
                                AppDiagnostics.failure("Updates", "confirmation", failure)
                                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            } finally {
                                working = false
                            }
                        }
                    }
                ) {
                    Text(when {
                        working -> "Проверка…"
                        loading -> "Загружается…"
                        downloaded -> "Проверить и установить"
                        else -> "Скачать обновление"
                    })
                }
            },
            dismissButton = {
                TextButton(enabled = !working, onClick = {
                    if (!loading) {
                        context.getSharedPreferences("musicflow_updates", Context.MODE_PRIVATE)
                            .edit()
                            .putLong("snoozed_version", update.versionCode)
                            .putLong("snoozed_until",
                                System.currentTimeMillis() + 24L * 60L * 60L * 1000L)
                            .apply()
                    }
                    announcementPreferences.edit()
                        .putLong("ignored_code", update.versionCode).apply()
                    hidden = true
                    available = null
                }) {
                    Text(if (loading) "Скачать в фоне" else "Позже", color = palette.accent)
                }
            }
        )
    }
}
