package com.hmedia.voidengine.features.epg

import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.helpers.DefaultHandler
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.xml.parsers.SAXParserFactory

data class EpgProgramme(
    val channelId: String,
    val startMs: Long,
    val stopMs: Long,
    val title: String,
    val description: String? = null
)

class EpgGuide(
    programmes: Map<String, List<EpgProgramme>>,
    val channelNames: Map<String, String>
) {
    /** Per channel, sorted by start time. */
    val programmesByChannel: Map<String, List<EpgProgramme>> =
        programmes.mapValues { entry -> entry.value.sortedBy { it.startMs } }

    fun nowAndNext(channelId: String, nowMs: Long): Pair<EpgProgramme?, EpgProgramme?> {
        val list = programmesByChannel[channelId] ?: return Pair(null, null)
        val currentIndex = list.indexOfFirst { nowMs >= it.startMs && nowMs < it.stopMs }
        if (currentIndex >= 0) return Pair(list[currentIndex], list.getOrNull(currentIndex + 1))
        return Pair(null, list.firstOrNull { it.startMs > nowMs })
    }
}

/**
 * XMLTV guide parser using the JVM/Android SAX parser. Pass a GZIPInputStream for .xml.gz guides.
 * The DTD is never fetched (entity resolver returns an empty document).
 */
object XmltvParser {

    /** Thrown when a guide is rejected for safety reasons (entity declarations, size limits). */
    class XmltvSecurityException(message: String) : IOException(message)

    const val DEFAULT_MAX_BYTES = 256L * 1024 * 1024
    const val DEFAULT_MAX_PROGRAMMES = 1_000_000
    private const val MAX_TEXT_CHARS = 65_536
    private const val SNIFF_BYTES = 64 * 1024

    /**
     * @param maxBytes maximum number of (decompressed) bytes read from [input].
     * @param maxProgrammes maximum number of programme entries kept.
     * @throws XmltvSecurityException for entity declarations or when a limit is exceeded.
     */
    fun parse(
        input: InputStream,
        maxBytes: Long = DEFAULT_MAX_BYTES,
        maxProgrammes: Int = DEFAULT_MAX_PROGRAMMES
    ): EpgGuide {
        val limited = BufferedInputStream(LimitedInputStream(input, maxBytes), SNIFF_BYTES + 1)
        rejectEntityDeclarations(limited)
        val factory = SAXParserFactory.newInstance()
        factory.isNamespaceAware = false
        factory.isValidating = false
        // Not every platform parser supports every feature, so each one is applied best-effort.
        // XXE is also blocked by the empty resolveEntity() below and by the entity sniff above.
        trySetFeature(factory, "http://javax.xml.XMLConstants/feature/secure-processing", true)
        trySetFeature(factory, "http://xml.org/sax/features/external-general-entities", false)
        trySetFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false)
        trySetFeature(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
        val handler = GuideHandler(maxProgrammes)
        try {
            factory.newSAXParser().parse(InputSource(limited), handler)
        } catch (e: org.xml.sax.SAXException) {
            // Parsers may wrap exceptions thrown by our handler/stream; surface the real reason.
            val inner = e.exception ?: e.cause
            if (inner is XmltvSecurityException) throw inner
            throw e
        }
        return EpgGuide(handler.programmes, handler.names)
    }

    private fun trySetFeature(factory: SAXParserFactory, name: String, value: Boolean) {
        try {
            factory.setFeature(name, value)
        } catch (e: Exception) {
            // feature not supported by this parser
        }
    }

    /**
     * Real XMLTV files often carry a plain `<!DOCTYPE tv SYSTEM "xmltv.dtd">`, so DOCTYPE itself is
     * allowed (and never fetched). Custom entity declarations (the basis of XXE and "billion laughs")
     * are not used by XMLTV and are rejected in the document prologue.
     */
    private fun rejectEntityDeclarations(stream: BufferedInputStream) {
        stream.mark(SNIFF_BYTES + 1)
        val head = ByteArray(SNIFF_BYTES)
        var total = 0
        while (total < head.size) {
            val n = stream.read(head, total, head.size - total)
            if (n < 0) break
            total += n
        }
        stream.reset()
        // Drop NUL bytes so UTF-16 encoded prologues are scanned as well.
        val text = String(head.copyOf(total).filter { it != 0.toByte() }.toByteArray(), Charsets.ISO_8859_1)
        if (text.contains("<!ENTITY")) throw XmltvSecurityException("XML entity declarations are not allowed")
        // Fail closed: entity declarations can only occur before the root element. If the root
        // element does not start inside the scanned window, the prologue was not fully inspected.
        if (total == SNIFF_BYTES && !text.contains("<tv")) {
            throw XmltvSecurityException("XML prologue is too long to verify")
        }
    }

    private class LimitedInputStream(source: InputStream, private val limit: Long) : FilterInputStream(source) {
        private var count = 0L
        private fun add(n: Int) {
            if (n > 0) {
                count += n
                if (count > limit) throw XmltvSecurityException("Guide exceeds $limit bytes")
            }
        }
        override fun read(): Int { val b = super.read(); if (b >= 0) add(1); return b }
        override fun read(b: ByteArray, off: Int, len: Int): Int { val n = super.read(b, off, len); add(n); return n }
    }

    private class GuideHandler(private val maxProgrammes: Int) : DefaultHandler() {
        private var programmeCount = 0
        val programmes = HashMap<String, MutableList<EpgProgramme>>()
        val names = HashMap<String, String>()

        private val withZone = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }
        private val withoutZone = SimpleDateFormat("yyyyMMddHHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
            isLenient = false
        }

        private var channel: String? = null
        private var start = 0L
        private var stop = 0L
        private var title: String? = null
        private var desc: String? = null
        private var inProgramme = false

        private var currentChannelId: String? = null
        private val text = StringBuilder()
        private var collecting = false

        override fun resolveEntity(publicId: String?, systemId: String?): InputSource =
            InputSource(StringReader(""))

        override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
            when (qName) {
                "programme" -> {
                    channel = attributes?.getValue("channel")
                    val s = parseTime(attributes?.getValue("start"))
                    val e = parseTime(attributes?.getValue("stop"))
                    inProgramme = channel != null && s != null && e != null
                    start = s ?: 0L
                    stop = e ?: 0L
                    title = null
                    desc = null
                }
                "channel" -> currentChannelId = attributes?.getValue("id")
                "title", "desc", "display-name" -> {
                    text.setLength(0)
                    collecting = true
                }
            }
        }

        override fun characters(ch: CharArray, offset: Int, length: Int) {
            if (collecting && text.length < MAX_TEXT_CHARS) {
                text.append(ch, offset, minOf(length, MAX_TEXT_CHARS - text.length))
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String?) {
            when (qName) {
                "title" -> {
                    if (inProgramme && title == null) title = text.toString().trim()
                    collecting = false
                }
                "desc" -> {
                    if (inProgramme && desc == null) desc = text.toString().trim()
                    collecting = false
                }
                "display-name" -> {
                    val id = currentChannelId
                    if (id != null && !names.containsKey(id)) names[id] = text.toString().trim()
                    collecting = false
                }
                "channel" -> currentChannelId = null
                "programme" -> {
                    val id = channel
                    if (inProgramme && id != null && stop > start) {
                        if (++programmeCount > maxProgrammes) {
                            throw XmltvSecurityException("Guide has more than $maxProgrammes programmes")
                        }
                        programmes.getOrPut(id) { ArrayList() }
                            .add(EpgProgramme(id, start, stop, title ?: "", desc))
                    }
                    inProgramme = false
                }
            }
        }

        private fun parseTime(value: String?): Long? {
            val parts = value?.trim()?.split(Regex("\\s+")) ?: return null
            val stamp = parts.firstOrNull() ?: return null
            if (stamp.length < 14) return null
            val zone = parts.getOrNull(1)
            return try {
                if (zone != null && Regex("[+-]\\d{4}").matches(zone)) {
                    withZone.parse("${stamp.substring(0, 14)} $zone")?.time
                } else {
                    withoutZone.parse(stamp.substring(0, 14))?.time
                }
            } catch (e: ParseException) {
                null
            }
        }
    }
}
