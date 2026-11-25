package com.musicflow.app

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import javax.xml.parsers.SAXParserFactory

internal data class TvProgramme(
    val channelId: String,
    val title: String,
    val startMillis: Long,
    val endMillis: Long
) {
    fun isNow(now: Long = System.currentTimeMillis()): Boolean =
        now >= startMillis && now < endMillis
}

internal object TvEpgParser {
    private val timeFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z", Locale.ROOT)

    internal fun parseTime(raw: String): Long? {
        val parts = raw.trim().split(Regex("\\s+"))
        val digits = parts.firstOrNull() ?: return null
        if (!digits.matches(Regex("\\d{14}"))) return null
        val offset = parts.getOrNull(1) ?: "+0000"
        if (!offset.matches(Regex("[+-]\\d{4}"))) return null
        return runCatching {
            OffsetDateTime.parse("$digits $offset", timeFormatter)
                .toInstant().toEpochMilli()
        }.getOrNull()
    }

    /** Secure SAX parsing: DTD/entity expansion is forbidden, payload already size-limited. */
    fun parse(payload: ByteArray): List<TvProgramme> {
        require(payload.size in 1..3_000_000) { "Неверный размер XMLTV" }
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            isValidating = false
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
        }
        val result = ArrayList<TvProgramme>()
        factory.newSAXParser().parse(ByteArrayInputStream(payload), object : DefaultHandler() {
            private var channel = ""
            private var start = 0L
            private var end = 0L
            private var inTitle = false
            private val title = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String?, a: Attributes) {
                when (qName) {
                    "programme" -> {
                        channel = a.getValue("channel").orEmpty().take(180)
                        start = parseTime(a.getValue("start").orEmpty()) ?: 0
                        end = parseTime(a.getValue("stop").orEmpty()) ?: 0
                        title.clear()
                    }
                    "title" -> if (channel.isNotBlank()) inTitle = true
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (inTitle && title.length < 160) {
                    title.append(ch, start, minOf(length, 160 - title.length))
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String?) {
                when (qName) {
                    "title" -> inTitle = false
                    "programme" -> {
                        val name = title.toString().trim()
                        if (channel.isNotBlank() && name.isNotBlank() &&
                            start > 0 && end > start && end - start <= 24 * 3600_000L &&
                            result.size < 4000) {
                            result.add(TvProgramme(channel, name, start, end))
                        }
                        channel = ""
                        title.clear()
                        inTitle = false
                    }
                }
            }
        })
        return result.sortedBy { it.startMillis }
    }
}
