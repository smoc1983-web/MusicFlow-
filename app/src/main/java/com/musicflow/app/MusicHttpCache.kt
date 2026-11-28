package com.musicflow.app

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File
import java.util.concurrent.TimeUnit

/** A bounded HTTP cache, separate from durable HLS/DASH downloads. */
@OptIn(UnstableApi::class)
internal object MusicHttpCache {
    private const val PREFS = "musicflow_music_http_cache"
    private const val LIMIT = "limit_mib"
    private const val DIR = "musicflow_music_http_v1"
    private data class Session(val limit: Int, val cache: Cache, val factory: DataSource.Factory)
    private var session: Session? = null

    fun limitMiB(context: Context): Int = MusicHttpCachePolicy.normalizeLimit(
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(LIMIT, MusicHttpCachePolicy.DEFAULT_MIB))

    fun setLimit(context: Context, limit: Int) {
        require(limit in MusicHttpCachePolicy.limitsMiB)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(LIMIT, limit).apply()
    }

    private fun directory(context: Context) = File(context.applicationContext.cacheDir, DIR)

    @Synchronized fun activeLimitMiB(context: Context): Int = session?.limit ?: limitMiB(context)

    @Synchronized fun source(context: Context): DataSource? {
        val limit = limitMiB(context)
        if (limit == 0) return null
        return try {
            val active = session ?: run {
                val cache = Cache(directory(context), limit.toLong() * 1024 * 1024)
                val client = OkHttpClient.Builder().cache(cache)
                    .connectTimeout(TvPlaybackTuning.CONNECT_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                    .readTimeout(TvPlaybackTuning.READ_TIMEOUT_MS.toLong(), TimeUnit.MILLISECONDS)
                    .addNetworkInterceptor(MusicHttpCacheInterceptor).build()
                Session(limit, cache, OkHttpDataSource.Factory(client)).also { session = it }
            }
            active.factory.createDataSource()
        } catch (error: Exception) {
            AppDiagnostics.failure("MusicCache", "HTTP cache unavailable", error)
            null
        }
    }

    @Synchronized fun usedBytes(context: Context): Long =
        session?.cache?.size() ?: directory(context).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    @Synchronized fun clear(context: Context) {
        val active = session
        if (active != null) active.cache.evictAll()
        else check(!directory(context).exists() || directory(context).deleteRecursively()) {
            "Не удалось очистить музыкальный кеш"
        }
    }
}

/** Use the original non-caching transport for ineligible requests and diagnostics. */
@OptIn(UnstableApi::class)
internal class MusicHttpCacheDataSource(
    private val context: Context,
    private val fallback: DataSource.Factory
) : DataSource {
    private val listeners = mutableListOf<TransferListener>()
    private var delegate: DataSource? = null

    override fun addTransferListener(transferListener: TransferListener) {
        if (transferListener !in listeners) {
            listeners.add(transferListener)
            delegate?.addTransferListener(transferListener)
        }
    }

    override fun open(dataSpec: DataSpec): Long {
        val allowed = dataSpec.httpMethod == DataSpec.HTTP_METHOD_GET &&
            dataSpec.position == 0L && dataSpec.length == C.LENGTH_UNSET.toLong() &&
            MusicHttpCachePolicy.eligible(dataSpec.uri.toString(), dataSpec.httpRequestHeaders.keys)
        val selected = (if (allowed) MusicHttpCache.source(context) else null)
            ?: fallback.createDataSource()
        delegate = selected
        listeners.forEach { selected.addTransferListener(it) }
        return selected.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
        checkNotNull(delegate).read(buffer, offset, length)

    override fun getUri(): Uri? = delegate?.uri
    override fun getResponseHeaders(): Map<String, List<String>> = delegate?.responseHeaders.orEmpty()

    override fun close() {
        try { delegate?.close() } finally { delegate = null }
    }
}
