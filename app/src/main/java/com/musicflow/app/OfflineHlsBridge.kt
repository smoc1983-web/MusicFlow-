package com.musicflow.app

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URI
import java.util.UUID
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap

/** Read-only cache contract. No network upstream is permitted. */
internal interface OfflineHlsCache {
    fun size(uri: String): Long
    fun read(uri: String, offset: Long, length: Long, output: OutputStream)
    fun baseUri(uri: String): String = uri
}

/** A capability-scoped HTTP endpoint. The caller owns and closes its lifetime. */
internal class OfflineHlsBridge private constructor(private val cache: OfflineHlsCache) : AutoCloseable {
    private data class Resource(val uri: String, val mime: String, val size: Long, val manifest: ByteArray? = null)
    private val server = ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
    private val token = UUID.randomUUID().toString()
    private val prefix = "/$token/"
    private val resources = LinkedHashMap<String, Resource>()
    private val ids = HashMap<String, String>()
    private val visiting = HashSet<String>()
    private val sockets = ConcurrentHashMap.newKeySet<Socket>()
    private val pool = ThreadPoolExecutor(4, 4, 30L, TimeUnit.SECONDS, ArrayBlockingQueue(16))
    @Volatile private var closed = false
    @Volatile var lastAccessNanos = System.nanoTime()
        private set
    lateinit var url: String
        private set

    companion object {
        fun open(root: String, variants: List<Int>, cache: OfflineHlsCache): OfflineHlsBridge {
            val bridge = OfflineHlsBridge(cache)
            try {
                bridge.url = bridge.playlist(root, variants)
                bridge.listen()
                return bridge
            } catch (error: Throwable) {
                bridge.close()
                throw error
            }
        }
    }

    private fun local(id: String) = "http://127.0.0.1:${server.localPort}$prefix$id"
    private fun idFor(uri: String, playlist: Boolean): String =
        ids.getOrPut(uri) {
            require(ids.size < 100000) { "Слишком большой HLS-плейлист" }
            UUID.randomUUID().toString() + if (playlist) ".m3u8" else ""
        }
    private fun resolve(base: String, value: String): String {
        val result = URI(base).resolve(value)
        require(result.scheme == "http" || result.scheme == "https") { "Неподдерживаемый HLS URI" }
        require(result.host != null && result.rawUserInfo == null && result.rawFragment == null)
        return result.toString()
    }
    private fun attr(line: String, name: String): String? =
        Regex("(?:^|[:,])" + Regex.escape(name) + "=(?:\"([^\"]*)\"|([^,]*))")
            .find(line)?.let { it.groups[1]?.value ?: it.groups[2]?.value }
    private fun uriAttribute(line: String, replacement: String): String =
        Regex("URI=\"[^\"]*\"").replaceFirst(line, "URI=\"$replacement\"")

    private fun segment(uri: String): String {
        val id = idFor(uri, false)
        if (!resources.containsKey(id)) {
            val size = cache.size(uri)
            require(size > 0) { "Сегмент HLS отсутствует или не полностью скачан" }
            val mime = when (URI(uri).path.substringAfterLast('.').lowercase()) {
                "ts" -> "video/mp2t"
                "mp4", "m4s" -> "video/mp4"
                "aac" -> "audio/aac"
                "vtt" -> "text/vtt"
                else -> "application/octet-stream"
            }
            resources[id] = Resource(uri, mime, size)
        }
        return local(id)
    }

    private fun playlist(uri: String, variants: List<Int> = emptyList()): String {
        val id = idFor(uri, true)
        if (resources.containsKey(id)) return local(id)
        require(visiting.add(uri) && visiting.size <= 5) { "Циклический HLS-плейлист" }
        try {
            val length = cache.size(uri)
            require(length in 1L..2097152L) { "Плейлист отсутствует или слишком велик" }
            val raw = ByteArrayOutputStream()
            cache.read(uri, 0, length, raw)
            val lines = raw.toString(Charsets.UTF_8.name()).lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
            require(lines.firstOrNull() == "#EXTM3U") { "Некорректный M3U8" }
            require(lines.none {
                it.startsWith("#EXT-X-SESSION-KEY") || it.startsWith("#EXT-X-DEFINE") ||
                    it.startsWith("#EXT-X-BYTERANGE") || it.startsWith("#EXT-X-PART") ||
                    it.startsWith("#EXT-X-PRELOAD-HINT") ||
                    (it.startsWith("#EXT-X-KEY:") && attr(it, "METHOD") != "NONE") ||
                    (it.startsWith("#EXT-X-MAP:") && attr(it, "BYTERANGE") != null)
            }) { "Защищённый или неподдерживаемый тип HLS; используйте встроенный плеер" }
            val base = cache.baseUri(uri)
            val master = lines.indices.filter { lines[it].startsWith("#EXT-X-STREAM-INF:") }
            val result = if (master.isEmpty()) media(lines, base) else master(lines, master, variants, base)
            val bytes = (result.joinToString("\n") + "\n").toByteArray(Charsets.UTF_8)
            resources[id] = Resource(uri, "application/vnd.apple.mpegurl", bytes.size.toLong(), bytes)
            return local(id)
        } finally {
            visiting.remove(uri)
        }
    }

    private fun media(lines: List<String>, base: String): List<String> {
        require("#EXT-X-ENDLIST" in lines && lines.any { it.startsWith("#EXTINF:") }) {
            "Поддерживается только завершённый HLS"
        }
        return lines.map { line ->
            when {
                !line.startsWith("#") -> segment(resolve(base, line))
                line.startsWith("#EXT-X-MAP:") -> {
                    val value = attr(line, "URI") ?: error("Нет URI init-сегмента")
                    uriAttribute(line, segment(resolve(base, value)))
                }
                line.startsWith("#EXT-X-KEY:") && attr(line, "METHOD") == "NONE" -> "#EXT-X-KEY:METHOD=NONE"
                line.contains("URI=", ignoreCase = true) -> error("Неподдерживаемая ссылка в HLS")
                else -> line
            }
        }
    }

    private fun master(lines: List<String>, indices: List<Int>, variants: List<Int>, base: String): List<String> {
        val choices = if (variants.isEmpty()) indices.indices.toList() else variants.distinct()
        for (choice in choices) {
            val index = indices.getOrNull(choice) ?: continue
            val tag = lines[index]
            val path = lines.getOrNull(index + 1)?.takeUnless { it.startsWith("#") } ?: continue
            // A failed variant must not leave unrelated resources exposed.
            val oldResources = resources.keys.toSet()
            val oldIds = ids.keys.toSet()
            val candidate = runCatching {
                val mediaUrl = playlist(resolve(base, path))
                val out = mutableListOf("#EXTM3U")
                lines.firstOrNull { it.startsWith("#EXT-X-VERSION:") }?.let(out::add)
                for (kind in listOf("AUDIO", "VIDEO", "SUBTITLES", "CLOSED-CAPTIONS")) {
                    val group = attr(tag, kind) ?: continue
                    if (kind == "CLOSED-CAPTIONS" && group == "NONE") continue
                    val matching = lines.filter { it.startsWith("#EXT-X-MEDIA:") &&
                        attr(it, "TYPE") == kind && attr(it, "GROUP-ID") == group }
                    require(matching.isNotEmpty()) { "Отсутствует группа дорожек" }
                    val available = mutableListOf<String>()
                    for (line in matching) {
                        val child = attr(line, "URI")
                        if (child == null) {
                            available += line
                        } else {
                            val before = resources.keys.toSet()
                            val beforeIds = ids.keys.toSet()
                            val childUrl = runCatching { playlist(resolve(base, child)) }.getOrNull()
                            if (childUrl != null) available += uriAttribute(line, childUrl)
                            else { resources.keys.retainAll(before); ids.keys.retainAll(beforeIds) }
                        }
                    }
                    require(available.isNotEmpty()) { "Дорожка отсутствует в кеше" }
                    out.addAll(available)
                }
                out += tag
                out += mediaUrl
                out
            }.getOrNull()
            if (candidate != null) return candidate
            resources.keys.retainAll(oldResources)
            ids.keys.retainAll(oldIds)
        }
        error("Нет полностью скачанного варианта HLS без защиты")
    }

    private fun listen() {
        Thread({
            while (!closed) {
                val socket = try { server.accept() } catch (_: Exception) { break }
                sockets.add(socket)
                try {
                    pool.execute {
                        try { socket.use { serve(it) } } catch (_: Exception) { }
                        finally { sockets.remove(socket) }
                    }
                } catch (_: Exception) { sockets.remove(socket); socket.close() }
            }
        }, "MusicFlow-HLS-listener").apply { isDaemon = true; start() }
    }

    private fun line(input: InputStream): String {
        val bytes = ByteArrayOutputStream()
        while (bytes.size() < 8192) {
            val b = input.read()
            if (b < 0 || b == 10) return bytes.toString("US-ASCII").trimEnd('\r')
            bytes.write(b)
        }
        error("HTTP header too large")
    }

    private fun serve(socket: Socket) {
        socket.soTimeout = 15000
        val input = socket.getInputStream().buffered()
        val request = line(input).split(' ')
        if (request.size != 3) return
        val method = request[0]
        val path = request[1]
        var range: String? = null
        var complete = false
        for (i in 0 until 80) {
            val header = line(input)
            if (header.isEmpty()) { complete = true; break }
            if (header.startsWith("Range:", true)) range = header.substringAfter(':').trim()
        }
        if (!complete) return
        val output = socket.getOutputStream().buffered()
        fun reply(code: String, extra: String = "") {
            output.write(("HTTP/1.1 $code\r\nConnection: close\r\nContent-Length: 0\r\n$extra\r\n").toByteArray(Charsets.US_ASCII))
            output.flush()
        }
        if (method != "GET" && method != "HEAD") { reply("405 Method Not Allowed"); return }
        val resource = if (path.startsWith(prefix)) resources[path.removePrefix(prefix)] else null
        if (resource == null) { reply("404 Not Found"); return }
        lastAccessNanos = System.nanoTime()
        val length = resource.size
        val selected = range?.let { parseRange(it, length) }
        if (range != null && selected == null) {
            reply("416 Range Not Satisfiable", "Content-Range: bytes */$length\r\n"); return
        }
        val start = selected?.first ?: 0L
        val end = selected?.last ?: (length - 1)
        val count = end - start + 1
        val status = if (selected == null) "200 OK" else "206 Partial Content"
        val contentRange = if (selected == null) "" else "Content-Range: bytes $start-$end/$length\r\n"
        output.write(("HTTP/1.1 $status\r\nConnection: close\r\nContent-Type: ${resource.mime}\r\nAccept-Ranges: bytes\r\nContent-Length: $count\r\n$contentRange\r\n").toByteArray(Charsets.US_ASCII))
        if (method == "GET") {
            if (resource.manifest != null) output.write(resource.manifest, start.toInt(), count.toInt())
            else cache.read(resource.uri, start, count, output)
        }
        output.flush()
        lastAccessNanos = System.nanoTime()
    }

    private fun parseRange(value: String, length: Long): LongRange? {
        val match = Regex("bytes=(\\d*)-(\\d*)").matchEntire(value) ?: return null
        val a = match.groupValues[1]; val b = match.groupValues[2]
        if (a.isEmpty()) {
            val suffix = b.toLongOrNull()?.takeIf { it > 0 } ?: return null
            return (length - suffix.coerceAtMost(length))..(length - 1)
        }
        val start = a.toLongOrNull()?.takeIf { it < length } ?: return null
        val end = if (b.isEmpty()) length - 1 else b.toLongOrNull()?.coerceAtMost(length - 1) ?: return null
        return if (end >= start) start..end else null
    }

    override fun close() {
        closed = true
        runCatching { server.close() }
        sockets.forEach { runCatching { it.close() } }
        pool.shutdownNow()
    }
}
