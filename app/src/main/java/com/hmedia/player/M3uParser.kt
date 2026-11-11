package com.hmedia.player

data class IptvChannel(val name: String, val url: String, val group: String)

// Reads an M3U or M3U8 playlist the user has supplied. Pure text handling, so it is unit tested.
object M3uParser {
    const val MAX_CHANNELS = 3000
    private val GROUP = Regex("group-title=\"([^\"]*)\"")
    private val SCHEMES = setOf("http", "https", "rtsp", "rtmp", "udp")

    // The channel name is the text after the first comma that is not inside quotes, so a comma in an
    // attribute value or in the name itself does not cut the name short.
    internal fun titleOf(extinf: String): String {
        var inQuotes = false
        for (i in extinf.indices) {
            val c = extinf[i]
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                return extinf.substring(i + 1).trim()
            }
        }
        return ""
    }

    // Keeps only channels with a web or stream address. Other lines are ignored.
    fun parse(text: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        var pendingName: String? = null
        var pendingGroup = ""
        // Some playlists start with a byte-order mark, which would hide the first line
        for (raw in text.removePrefix("\uFEFF").lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (line.startsWith("#EXTINF")) {
                pendingName = titleOf(line).ifEmpty { "Channel" }
                pendingGroup = GROUP.find(line)?.groupValues?.get(1)?.trim().orEmpty()
                continue
            }
            if (line.startsWith("#")) continue
            val scheme = line.substringBefore("://", "").lowercase()
            if (scheme in SCHEMES) {
                channels += IptvChannel(pendingName ?: line, line, pendingGroup)
                if (channels.size >= MAX_CHANNELS) break
            }
            pendingName = null
            pendingGroup = ""
        }
        return channels
    }
}
