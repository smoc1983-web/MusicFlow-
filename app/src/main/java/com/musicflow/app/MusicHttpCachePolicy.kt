package com.musicflow.app

import java.net.URI
import java.util.Locale
import okhttp3.CacheControl
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response

/** Ordinary HTTP cache only. No forced offline access, signed URLs or auth. */
internal object MusicHttpCachePolicy {
    val limitsMiB = listOf(0, 256, 512, 1024, 2048)
    const val DEFAULT_MIB = 2048

    fun normalizeLimit(value: Int): Int = value.takeIf { it in limitsMiB } ?: DEFAULT_MIB

    fun eligible(url: String, requestHeaders: Set<String> = emptySet()): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase(Locale.ROOT) ?: return false
        if (!uri.scheme.equals("https", true) || uri.rawUserInfo != null ||
            uri.rawQuery != null || uri.rawFragment != null ||
            requestHeaders.any { it.equals("Authorization", true) || it.equals("Cookie", true) }) return false
        val path = uri.path.orEmpty()
        if (path.isBlank() || path == "/") return false
        val extension = path.substringAfterLast('.', "").lowercase(Locale.ROOT)
        if (extension in setOf("m3u8", "mpd", "mp4", "mkv", "webm")) return false
        return extension in setOf("mp3", "m4a", "aac", "flac", "ogg", "opus", "wav") ||
            host == "muzofond.fm" || host.endsWith(".muzofond.fm") ||
            host == "my.mail.ru" || host.endsWith(".my.mail.ru")
    }

    fun mayStore(url: String, requestHeaders: Set<String>, status: Int, headers: Headers): Boolean {
        if (status != 200 || !eligible(url, requestHeaders) ||
            headers["Set-Cookie"] != null || headers["Content-Range"] != null) return false
        val type = headers["Content-Type"].orEmpty().substringBefore(';').trim()
        if (!type.startsWith("audio/", ignoreCase = true)) return false
        val length = headers["Content-Length"]?.toLongOrNull() ?: return false
        if (length !in 1L..(100L * 1024 * 1024)) return false
        val control = CacheControl.parse(headers)
        return control.isPublic && control.maxAgeSeconds > 0 &&
            !control.noStore && !control.noCache && !control.isPrivate
    }
}

internal object MusicHttpCacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (MusicHttpCachePolicy.mayStore(response.request.url.toString(),
                response.request.headers.names(), response.code, response.headers)) return response
        // Strengthen, never relax, origin cache restrictions.
        return response.newBuilder().header("Cache-Control", "no-store").build()
    }
}
