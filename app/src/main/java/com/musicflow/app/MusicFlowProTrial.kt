package com.musicflow.app

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.content.ContentValues
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Base64

/**
 * Development-only integration of the proven signed *data* patch.
 *
 * This is not a real billing entitlement. Only the official test-tone fixture is
 * downloadable. NEVER expose this as a live Pro entitlement / production downloader.
 */
internal object MusicFlowProTrial {
    private const val PATCH_BASE =
        "https://raw.githubusercontent.com/smoc1983-web/MusicFlow-/" +
        "feature/musicflow-pro-demo-patch/pro-patch-test/patch/"
    private const val PAYLOAD = "musicflow-pro-test:v1:enable_download"
    private const val PUBLIC_KEY =
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA2bbyZszazpsLFwwjrzyWN34gEzHpDjkCq67T7bS4z/fWZFSCSkw1esf2HliAxdHa/ftxTNh3JHAFDDwFcSV56RLtnQrl7DazaRF6sMbjjvE9gSQz8l3ATblUDmhp7U49zoWffTyNLPRyuj+hPJBcDh2fg+nncgKGApDXjpRt5nUM0m6gwlS6u5qvNFRVWnc5UOVbugrJIQP4mJtrg8eiJMnq7VQUEJZvqvAIKu6xUIkEon7IVvw2l0kkqaflIK+Nla9joUJRLtc7qFPWByjkLOGVDvgkCOjEnH0hyvOoU7zbNczInAMQCg2ZFYpMraYBaFK9T2vAySV+PK6qeArTpQIDAQAB"

    internal fun isUnlocked(mockPaid: Boolean, signedPatchVerified: Boolean): Boolean =
        mockPaid && signedPatchVerified

    internal fun isValidFixtureWav(bytes: ByteArray): Boolean {
        if (bytes.size !in 45..32_000) return false
        val riff = byteArrayOf(0x52, 0x49, 0x46, 0x46)
        val wave = byteArrayOf(0x57, 0x41, 0x56, 0x45)
        return bytes.copyOfRange(0, 4).contentEquals(riff) &&
            bytes.copyOfRange(8, 12).contentEquals(wave) &&
            bytes.copyOfRange(36, 40).contentEquals(byteArrayOf(0x64, 0x61, 0x74, 0x61))
    }

    internal fun verifySignedPatch(payload: String, signature64: String): Boolean {
        if (payload != PAYLOAD) return false
        return runCatching {
            val publicKey = KeyFactory.getInstance("RSA").generatePublic(
                X509EncodedKeySpec(Base64.getDecoder().decode(PUBLIC_KEY))
            )
            val verifier = Signature.getInstance("SHA256withRSA")
            verifier.initVerify(publicKey)
            verifier.update(payload.toByteArray(StandardCharsets.UTF_8))
            verifier.verify(Base64.getDecoder().decode(signature64))
        }.getOrDefault(false)
    }

    suspend fun fetchAndVerifyPatch(): Int = withContext(Dispatchers.IO) {
        val data = fetch(PATCH_BASE + "activation.json", 8_000)
        val json = JSONObject(String(data, StandardCharsets.UTF_8))
        if (!verifySignedPatch(json.getString("payload"), json.getString("signature"))) {
            throw SecurityException("Подпись микро-патча не совпадает")
        }
        data.size
    }

    suspend fun downloadOriginalTestTone(context: Context): Uri = withContext(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT < 29) {
            throw IOException("Для демонстрационной загрузки нужен Android 10 или новее")
        }
        val encoded = fetch(PATCH_BASE + "demo-track.wav.b64", 45_000)
        val decoded = Base64.getMimeDecoder().decode(
            String(encoded, StandardCharsets.US_ASCII).trim()
        )
        if (!isValidFixtureWav(decoded)) {
            throw IOException("Загруженный тестовый WAV не прошёл проверку формата")
        }

        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "MusicFlow_Pro_Test.wav")
            put(MediaStore.Audio.Media.MIME_TYPE, "audio/wav")
            put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/MusicFlow Pro Test")
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Android не создал аудиофайл")
        var success = false
        try {
            val output = resolver.openOutputStream(uri)
                ?: throw IOException("Нет доступа для записи WAV")
            output.use { it.write(decoded); it.flush() }
            val ready = ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }
            if (resolver.update(uri, ready, null, null) != 1) {
                throw IOException("Android не завершил сохранение WAV")
            }
            success = true
            uri
        } finally {
            if (!success) resolver.delete(uri, null, null)
        }
    }

    private fun fetch(address: String, maximum: Int): ByteArray {
        val connection = URL(address).openConnection() as HttpURLConnection
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = false
        connection.setRequestProperty("Accept", "application/json, text/plain, application/octet-stream")
        return try {
            if (connection.responseCode != 200) {
                throw IOException("GitHub ответил HTTP " + connection.responseCode)
            }
            connection.inputStream.use { stream ->
                val result = ByteArrayOutputStream()
                val buffer = ByteArray(4_096)
                while (true) {
                    val size = stream.read(buffer)
                    if (size < 0) break
                    if (result.size() + size > maximum) {
                        throw IOException("Размер ответа превышает лимит")
                    }
                    if (size > 0) result.write(buffer, 0, size)
                }
                result.toByteArray()
            }
        } finally {
            connection.disconnect()
        }
    }
}

@Composable
internal fun MusicFlowProTrialDialog(onDismiss: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val preferences = remember(context) {
        context.getSharedPreferences("musicflow_pro_stage1_trial", Context.MODE_PRIVATE)
    }
    val scope = rememberCoroutineScope()
    var paid by remember { mutableStateOf(preferences.getBoolean("mock_paid", false)) }
    var patchVerified by remember { mutableStateOf(preferences.getBoolean("signed_patch", false)) }
    var message by remember { mutableStateOf("Тестовый режим: сначала имитация оплаты, затем подписанный патч.") }
    var busy by remember { mutableStateOf(false) }
    val unlocked = MusicFlowProTrial.isUnlocked(paid, patchVerified)

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("MusicFlow Pro · тест") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text("Отдельная ветка Pro. Реальная подписка и коммерческие треки пока отключены.")
                Spacer(Modifier.height(10.dp))
                Text("Тестовая оплата: " + if (paid) "подтверждена" else "нет")
                Text("Подписанный микро-патч: " + if (patchVerified) "проверен" else "не загружен")
                Text("Демо Pro: " + if (unlocked) "разблокировано" else "заблокировано")
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    paid = true
                    preferences.edit().putBoolean("mock_paid", true).apply()
                    message = "Имитация оплаты завершена. Деньги не списывались."
                }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("1. Имитировать оплату")
                }
                Button(onClick = {
                    busy = true
                    scope.launch {
                        message = try {
                            val bytes = MusicFlowProTrial.fetchAndVerifyPatch()
                            patchVerified = true
                            preferences.edit().putBoolean("signed_patch", true).apply()
                            "Подпись верна. Получено $bytes байт из отдельной ветки GitHub."
                        } catch (e: Exception) {
                            patchVerified = false
                            preferences.edit().remove("signed_patch").apply()
                            "Ошибка патча: " + (e.message ?: "неизвестная")
                        } finally {
                            busy = false
                        }
                    }
                }, enabled = paid && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("2. Получить микро-патч")
                }
                Button(onClick = {
                    busy = true
                    scope.launch {
                        message = try {
                            val uri = MusicFlowProTrial.downloadOriginalTestTone(context)
                            "Тестовый WAV сохранён в Music/MusicFlow Pro Test: $uri"
                        } catch (e: Exception) {
                            "Ошибка загрузки: " + (e.message ?: "неизвестная")
                        } finally {
                            busy = false
                        }
                    }
                }, enabled = unlocked && !busy, modifier = Modifier.fillMaxWidth()) {
                    Text("3. Скачать тестовый WAV")
                }
                TextButton(onClick = {
                    paid = false
                    patchVerified = false
                    preferences.edit().clear().apply()
                    message = "Тестовая лицензия сброшена; новые скачивания заблокированы."
                }, enabled = !busy) {
                    Text("Сбросить тестовую активацию")
                }
                Text(message)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss, enabled = !busy) { Text("Закрыть") }
        }
    )
}
