package com.musicflow.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

internal enum class IptvSourceKind { PLAYLIST, CHANNEL }

internal data class IptvSource(
    val id: String,
    val name: String,
    val address: String,
    val kind: IptvSourceKind
)

private const val IPTV_PREFS = "musicflow_custom_iptv_v1"
private const val IPTV_ITEMS = "sources"

internal fun isSupportedIptvUrl(text: String): Boolean {
    val parsed = runCatching { Uri.parse(text.trim()) }.getOrNull() ?: return false
    val scheme = parsed.scheme?.lowercase()
    return (scheme == "http" || scheme == "https") && !parsed.host.isNullOrBlank() &&
        text.length <= 4096 && !text.any { it == '\n' || it == '\r' }
}

internal fun isCubPluginUrl(text: String): Boolean {
    val parsed = runCatching { Uri.parse(text.trim()) }.getOrNull() ?: return false
    val host = parsed.host?.lowercase().orEmpty()
    return (host == "cub.red" || host == "cub.best") &&
        parsed.path.orEmpty().trimEnd('/').equals("/plugin/iptv", ignoreCase = true)
}

internal fun loadIptvSources(context: Context): List<IptvSource> = runCatching {
    val text = context.getSharedPreferences(IPTV_PREFS, Context.MODE_PRIVATE)
        .getString(IPTV_ITEMS, "[]") ?: "[]"
    val array = JSONArray(text)
    buildList {
        for (i in 0 until minOf(array.length(), 40)) {
            val item = array.optJSONObject(i) ?: continue
            val id = item.optString("id")
            val name = item.optString("name")
            val address = item.optString("address")
            val kind = runCatching {
                IptvSourceKind.valueOf(item.optString("kind"))
            }.getOrNull() ?: continue
            if (id.isNotBlank() && name.isNotBlank() && isSupportedIptvUrl(address)) {
                add(IptvSource(id, name, address, kind))
            }
        }
    }
}.getOrDefault(emptyList())

internal fun saveIptvSources(context: Context, sources: List<IptvSource>) {
    val array = JSONArray()
    sources.take(40).forEach { source ->
        array.put(JSONObject().apply {
            put("id", source.id)
            put("name", source.name)
            put("address", source.address)
            put("kind", source.kind.name)
        })
    }
    context.getSharedPreferences(IPTV_PREFS, Context.MODE_PRIVATE)
        .edit().putString(IPTV_ITEMS, array.toString()).apply()
}

internal fun newIptvSource(
    name: String,
    address: String,
    kind: IptvSourceKind
): IptvSource {
    val url = address.trim()
    require(isSupportedIptvUrl(url)) { "Нужна ссылка HTTP или HTTPS." }
    require(!isCubPluginUrl(url)) {
        "Адрес CUB — плагин для Lampa, а не M3U-плейлист. Добавьте прямую ссылку на плейлист из CUB или от провайдера."
    }
    return IptvSource(
        id = UUID.randomUUID().toString(),
        name = name.trim().ifBlank { if (kind == IptvSourceKind.CHANNEL) "Мой канал" else "Мой плейлист" }.take(70),
        address = url,
        kind = kind
    )
}

internal suspend fun fetchIptvSource(source: IptvSource): List<TvChannel> = withContext(Dispatchers.IO) {
    if (source.kind == IptvSourceKind.CHANNEL) {
        return@withContext listOf(
            TvChannel(
                id = "custom:" + source.id + ":stream",
                title = source.name,
                group = "Мои каналы",
                logo = null,
                streamUrl = source.address
            )
        )
    }
    val connection = (URL(source.address).openConnection() as HttpURLConnection).apply {
        connectTimeout = 12_000
        readTimeout = 20_000
        instanceFollowRedirects = true
        setRequestProperty("Accept", "*/*")
        setRequestProperty("User-Agent", "MusicFlowAndroid/0.6")
    }
    val text = try {
        if (connection.responseCode !in 200..299) {
            error("Сервер плейлиста: HTTP " + connection.responseCode)
        }
        val result = StringBuilder()
        connection.inputStream.bufferedReader().use { reader ->
            val buffer = CharArray(8192)
            while (result.length < 1_000_000) {
                val count = reader.read(buffer, 0, minOf(buffer.size, 1_000_000 - result.length))
                if (count <= 0) break
                result.append(buffer, 0, count)
            }
        }
        result.toString()
    } finally {
        connection.disconnect()
    }
    parseUserIptvPlaylist(text, source)
}

private fun parseUserIptvPlaylist(raw: String, source: IptvSource): List<TvChannel> {
    val text = raw.trimStart('\uFEFF', ' ', '\n', '\r', '\t')
    require(text.startsWith("#EXTM3U", ignoreCase = true)) {
        "Ссылка не возвращает M3U-плейлист. Для одного потока выберите тип «Канал»."
    }
    require(!text.contains("#EXT-X-TARGETDURATION") &&
        !text.contains("#EXT-X-STREAM-INF")) {
        "Это поток HLS, а не список каналов. Добавьте его как «Канал»."
    }

    val result = mutableListOf<TvChannel>()
    var info: String? = null
    var group = "Без категории"
    for (rawLine in text.lineSequence()) {
        val line = rawLine.trim()
        when {
            line.startsWith("#EXTINF:", true) -> {
                info = line
                group = iptvAttribute(line, "group-title") ?: "Без категории"
            }
            line.startsWith("#EXTGRP:", true) -> {
                group = line.substringAfter(':').trim().ifBlank { group }
            }
            line.isBlank() || line.startsWith("#") -> Unit
            info != null -> {
                val lastInfo = info ?: continue
                info = null
                val stream = runCatching { URL(URL(source.address), line).toExternalForm() }.getOrNull()
                    ?: continue
                if (!isSupportedIptvUrl(stream)) continue
                val title = (iptvAttribute(lastInfo, "tvg-name")
                    ?: lastInfo.substringAfter(',', "").trim()).ifBlank {
                    "Канал " + (result.size + 1)
                }
                val slug = iptvAttribute(lastInfo, "tvg-id")?.takeIf { it.isNotBlank() }
                    ?: result.size.toString()
                val logo = iptvAttribute(lastInfo, "tvg-logo")
                    ?.takeIf { isSupportedIptvUrl(it) }
                result.add(
                    TvChannel(
                        id = "custom:" + source.id + ":" + slug + ":" + result.size,
                        title = title.take(100),
                        group = group.take(70),
                        logo = logo,
                        streamUrl = stream
                    )
                )
                if (result.size >= 5000) break
            }
        }
    }
    require(result.isNotEmpty()) { "В M3U не найдено ни одного подходящего HTTP(S)-канала." }
    return result
}

private fun iptvAttribute(line: String, name: String): String? =
    Regex(Regex.escape(name) + "=\"([^\"]*)\"")
        .find(line)?.groupValues?.getOrNull(1)?.takeIf { it.isNotBlank() }

internal fun openTvExternal(context: Context, channel: TvChannel): String? {
    if (!isSupportedIptvUrl(channel.streamUrl)) return "Неверная ссылка на трансляцию."
    return runCatching {
        val uri = Uri.parse(channel.streamUrl)
        val viewIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            putExtra("title", channel.title)
            putExtra(Intent.EXTRA_TITLE, channel.title)
        }
        context.startActivity(Intent.createChooser(viewIntent, "Открыть эфир через…"))
        null
    }.getOrElse { "Не удалось открыть внешний плеер: " + (it.message ?: "нет подходящего приложения") }
}
