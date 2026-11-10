package com.hmedia.voidengine.features.iptv

import com.hmedia.voidengine.PlaybackSource
import com.hmedia.voidengine.SourceType
import com.hmedia.voidengine.features.url.SourceClassifier

data class IptvChannel(
    val id: String,
    val name: String,
    val url: String,
    val logoUrl: String? = null,
    val group: String? = null,
    val tvgId: String? = null,
    val userAgent: String? = null,
    val referrer: String? = null
) {
    /** Ready to hand to VoidEngine.open(). */
    fun toSource(): PlaybackSource {
        val headers = HashMap<String, String>()
        userAgent?.let { headers["User-Agent"] = it }
        referrer?.let { headers["Referer"] = it }
        val detected = SourceClassifier.classify(url)
        val type = if (detected == SourceType.HTTP) SourceType.IPTV_STREAM else detected
        return PlaybackSource(uri = url, type = type, title = name, headers = headers)
    }
}

/** Parses extended M3U / M3U8 playlists (#EXTINF, #EXTGRP, #EXTVLCOPT). Pure Kotlin. */
object M3uParser {
    private val attribute = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")

    private class Pending(
        var name: String = "",
        var attrs: Map<String, String> = emptyMap(),
        var group: String? = null,
        var userAgent: String? = null,
        var referrer: String? = null
    )

    fun parse(text: String): List<IptvChannel> {
        val channels = ArrayList<IptvChannel>()
        var pending: Pending? = null
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#")) {
                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> pending = parseExtInf(line)
                    line.startsWith("#EXTGRP:", ignoreCase = true) ->
                        pending?.let { if (it.group == null) it.group = line.substringAfter(':').trim() }
                    line.startsWith("#EXTVLCOPT:", ignoreCase = true) -> {
                        val option = line.substringAfter(':')
                        val key = option.substringBefore('=').trim().lowercase()
                        val value = option.substringAfter('=', "").trim()
                        pending?.let {
                            if (key == "http-user-agent") it.userAgent = value
                            if (key == "http-referrer") it.referrer = value
                        }
                    }
                }
                continue
            }
            val current = pending
            val name = current?.name?.ifEmpty { null } ?: line.substringBefore('?').substringAfterLast('/')
            val tvgId = current?.attrs?.get("tvg-id")?.ifEmpty { null }
            channels.add(
                IptvChannel(
                    id = tvgId ?: line,
                    name = name,
                    url = line,
                    logoUrl = current?.attrs?.get("tvg-logo")?.ifEmpty { null },
                    group = (current?.attrs?.get("group-title")?.ifEmpty { null }) ?: current?.group,
                    tvgId = tvgId,
                    userAgent = current?.userAgent,
                    referrer = current?.referrer
                )
            )
            pending = null
        }
        return channels
    }

    private fun parseExtInf(line: String): Pending {
        val lastQuote = line.lastIndexOf('"')
        val comma = line.indexOf(',', if (lastQuote >= 0) lastQuote else 0)
        val head = if (comma >= 0) line.substring(0, comma) else line
        val name = if (comma >= 0) line.substring(comma + 1).trim() else ""
        val attrs = HashMap<String, String>()
        for (match in attribute.findAll(head)) {
            attrs[match.groupValues[1].lowercase()] = match.groupValues[2]
        }
        return Pending(name = name, attrs = attrs)
    }
}

fun List<IptvChannel>.groupedByGroup(): Map<String, List<IptvChannel>> = groupBy { it.group ?: "Other" }
