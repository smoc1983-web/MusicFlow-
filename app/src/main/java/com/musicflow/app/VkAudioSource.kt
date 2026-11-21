package com.musicflow.app

import android.content.Context
import android.net.Uri
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Native, opt-in VK music source. VK restricts audio.search to approved clients:
 * normal VK ID sign-in and ordinary OAuth tokens do NOT grant streaming access.
 *
 * This provider uses only the public VK API endpoint and an access token explicitly
 * provided on the device by a user who has VK audio API entitlement. It never
 * impersonates an official VK application, captures browser cookies, or bypasses DRM.
 * A result is exposed as a MusicFlow Track only if VK returns an HTTPS audio URL.
 */
internal object VkAudioSource {
    private const val API = "https://api.vk.com/method/"
    private const val API_VERSION = "5.199"
    private const val PREFERENCES = "musicflow_vk_private"
    private const val TOKEN_CIPHERTEXT = "audio_access_token"
    private const val TOKEN_IV = "audio_access_token_iv"
    private const val KEY_ALIAS = "com.musicflow.app.vk.audio.credentials.v1"
    @Volatile private var application: Context? = null

    fun initialize(context: Context) {
        application = context.applicationContext
    }

    fun isConfigured(): Boolean = !readToken().isNullOrBlank()

    /** The token is encrypted by a nonexportable Android Keystore AES-GCM key. */
    fun saveToken(rawToken: String) {
        val token = rawToken.trim()
        require(token.length in 10..2048 && !token.any { it.isWhitespace() }) {
            "Некорректный формат токена VK"
        }
        val context = application ?: throw IllegalStateException("VK source not initialized")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, encryptionKey())
        val bytes = cipher.doFinal(token.toByteArray(Charsets.UTF_8))
        val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        check(prefs.edit()
            .putString(TOKEN_CIPHERTEXT, Base64.encodeToString(bytes, Base64.NO_WRAP))
            .putString(TOKEN_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()) { "Не удалось сохранить настройки VK" }
    }

    fun disconnect() {
        application?.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)?.edit()
            ?.remove(TOKEN_CIPHERTEXT)?.remove(TOKEN_IV)?.apply()
    }

    private fun readToken(): String? {
        val context = application ?: return null
        return runCatching {
            val prefs = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            val data = prefs.getString(TOKEN_CIPHERTEXT, null) ?: return@runCatching null
            val iv = prefs.getString(TOKEN_IV, null) ?: return@runCatching null
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, encryptionKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)))
            String(cipher.doFinal(Base64.decode(data, Base64.NO_WRAP)), Charsets.UTF_8)
        }.getOrNull()
    }

    private fun encryptionKey(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(
                KEY_ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
            ).setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build())
        }.generateKey()
    }

    /**
     * The same native searchMusic() pipeline handles this source and all others.
     * When disconnected, VK returns no results without degrading other providers.
     */
    suspend fun search(query: String): List<Track> = withContext(Dispatchers.IO) {
        val token = readToken() ?: return@withContext emptyList()
        val requested = query.trim()
        if (requested.isBlank()) return@withContext emptyList()
        val response = apiRequest(
            "audio.search", token,
            mapOf("q" to requested.take(160), "count" to "75", "auto_complete" to "1")
        )
        parseSearchResults(response)
    }

    suspend fun testAccess(): String = withContext(Dispatchers.IO) {
        val token = readToken() ?: return@withContext "Не введён ключ доступа"
        val response = apiRequest(
            "audio.search", token,
            mapOf("q" to "music", "count" to "1")
        )
        if (response.optJSONObject("response") != null) {
            "Метод audio.search доступен. Воспроизведение зависит от прав на конкретные записи."
        } else {
            "VK не вернул результаты проверки"
        }
    }

    /** No placeholder rows: nonplayable, DRM-only or hidden-URL records are excluded. */
    internal fun parseSearchResults(root: JSONObject): List<Track> {
        val records = root.optJSONObject("response")?.optJSONArray("items")
            ?: return emptyList()
        val tracks = ArrayList<Track>(records.length())
        for (index in 0 until minOf(records.length(), 100)) {
            val audio = records.optJSONObject(index) ?: continue
            val owner = audio.optLong("owner_id", 0L)
            val id = audio.optLong("id", 0L)
            if (owner == 0L || id <= 0L) continue
            val artist = audio.optString("artist").trim()
            val title = audio.optString("title").trim()
            if (artist.isBlank() || title.isBlank()) continue
            val url = audio.optString("url").trim()
            if (!isPlayableUrl(url)) continue
            val album = audio.optJSONObject("album")
            val thumb = album?.optJSONObject("thumb")
            val cover = listOf("photo_600", "photo_300", "photo_270", "photo_135")
                .firstNotNullOfOrNull { key ->
                    thumb?.optString(key)?.takeIf { it.startsWith("https://") }
                }
            tracks += Track(
                id = "vk:${owner}_${id}",
                title = title.take(180),
                artist = artist.take(180),
                artwork = cover,
                stream = url,
                durationSec = audio.optLong("duration", 0L).coerceIn(0L, 86_400L),
                collectionTitle = album?.optString("title")?.trim()?.takeIf { it.isNotEmpty() }
            )
        }
        return tracks.distinctBy { it.id }
    }

    internal fun isPlayableUrl(value: String): Boolean {
        val uri = runCatching { Uri.parse(value) }.getOrNull() ?: return false
        return uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() &&
            uri.encodedUserInfo.isNullOrBlank() && value.length <= 4096 &&
            !value.contains("audio_api_unavailable", ignoreCase = true)
    }

    private fun apiRequest(
        method: String,
        token: String,
        params: Map<String, String>
    ): JSONObject {
        val body = (params + mapOf("v" to API_VERSION, "access_token" to token))
            .entries.joinToString("&") { (key, value) ->
                URLEncoder.encode(key, "UTF-8") + "=" +
                    URLEncoder.encode(value, "UTF-8")
            }.toByteArray(Charsets.UTF_8)
        val connection = (URL(API + method).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = 9_000
            readTimeout = 14_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8")
            setRequestProperty("Cache-Control", "no-store")
        }
        return try {
            connection.outputStream.use { it.write(body) }
            val status = connection.responseCode
            if (status !in 200..299) throw ProviderHttpException(status)
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val chars = CharArray(8192)
                val buffer = StringBuilder()
                while (buffer.length < 800_000) {
                    val count = reader.read(chars, 0, minOf(chars.size, 800_000 - buffer.length))
                    if (count <= 0) break
                    buffer.append(chars, 0, count)
                }
                buffer.toString()
            }
            val data = JSONObject(response)
            data.optJSONObject("error")?.let { error ->
                val code = error.optInt("error_code", -1)
                when (code) {
                    3, 7, 15, 201 -> throw IOException(
                        "VK отклонил audio API (код $code). Требуется разрешённый музыкальный доступ."
                    )
                    5 -> throw IOException("VK: неверный или истёкший токен (код 5).")
                    6, 9 -> throw IOException("VK: ограничение частоты запросов (код $code).")
                    else -> throw IOException("VK Audio API вернул ошибку $code.")
                }
            }
            data
        } finally {
            connection.disconnect()
        }
    }
}

internal suspend fun vkTracks(query: String): List<Track> = VkAudioSource.search(query)
