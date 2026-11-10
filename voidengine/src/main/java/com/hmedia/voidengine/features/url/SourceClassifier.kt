package com.hmedia.voidengine.features.url

import com.hmedia.voidengine.PlaybackSource
import com.hmedia.voidengine.SourceType
import java.util.Locale

/** Direct-URL streaming helpers. Pure Kotlin (no Android types) so it can be unit-tested on the JVM. */
object SourceClassifier {

    private val LOCAL_SCHEMES = setOf("file", "content", "vault", "asset", "android.resource")
    private val INPUT_SCHEMES = setOf(
        "http", "https", "rtsp", "rtsps", "rtmp", "rtmps", "rtmpt", "rtp", "udp", "mms", "mmsh", "ftp",
        "sftp", "smb", "file", "content"
    )

    /** Lower-case scheme, "" when there is none. A bare absolute path counts as "file". */
    fun schemeOf(url: String): String {
        val index = url.indexOf("://")
        if (index <= 0) return if (url.startsWith("/")) "file" else ""
        return url.substring(0, index).lowercase(Locale.ROOT)
    }

    /** Lower-case file extension without the dot, ignoring query and fragment; null if none. */
    fun extensionOf(url: String): String? {
        val path = url.substringBefore('#').substringBefore('?')
        // "https://host.example" has no path, so ".example" is not an extension.
        if (path.contains("://") && !path.substringAfter("://").contains('/')) return null
        val name = path.substringAfterLast('/')
        if (!name.contains('.')) return null
        val ext = name.substringAfterLast('.').lowercase(Locale.ROOT)
        return if (ext.isEmpty()) null else ext
    }

    fun classify(url: String): SourceType {
        val scheme = schemeOf(url)
        val path = url.substringBefore('#').substringBefore('?').lowercase(Locale.ROOT)
        return when {
            scheme == "rtsp" || scheme == "rtsps" -> SourceType.RTSP
            scheme == "rtmp" || scheme == "rtmps" || scheme == "rtmpt" -> SourceType.RTMP
            scheme == "rtp" || scheme == "udp" -> SourceType.RTP_UDP
            scheme == "mms" || scheme == "mmsh" -> SourceType.MMS
            scheme == "ftp" -> SourceType.FTP
            scheme == "sftp" || scheme == "smb" -> SourceType.NETWORK_SHARE
            scheme in LOCAL_SCHEMES -> SourceType.LOCAL_FILE
            path.endsWith(".m3u8") -> SourceType.HLS
            path.endsWith(".mpd") -> SourceType.DASH
            scheme == "http" || scheme == "https" ->
                if (path.endsWith(".ts")) SourceType.IPTV_STREAM else SourceType.HTTP
            else -> SourceType.UNKNOWN
        }
    }

    /** True for a URL/path whose scheme is explicitly supported by [classify] (never UNKNOWN). */
    fun isSupported(url: String): Boolean = classify(url) != SourceType.UNKNOWN

    /**
     * Rejects values that must never reach a backend: control characters, "user:pass@" credentials
     * embedded in a remote URL (pass them as headers instead), and remote URLs without a host.
     * Returns null when [url] is acceptable, otherwise a short reason.
     */
    fun validationError(url: String): String? {
        if (url.any { it.isISOControl() }) return "Control characters are not allowed in a source URL"
        val type = classify(url)
        if (type == SourceType.UNKNOWN) return "Unsupported or missing URL scheme"
        if (type == SourceType.LOCAL_FILE) return null
        var authority = url.substringAfter("://", "").substringBefore('/').substringBefore('?').substringBefore('#')
        // "udp://@239.0.0.1:1234" is the standard multicast form, not a credential.
        if (type == SourceType.RTP_UDP) authority = authority.removePrefix("@")
        if (authority.isEmpty() && type != SourceType.RTP_UDP) return "Missing host"
        if (authority.contains('@')) return "Credentials in the URL are not allowed"
        return null
    }

    /** Turns text typed or pasted by a user into a source, or null if it is not a playable URL/path. */
    fun fromUserInput(text: String): PlaybackSource? {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed.any { it.isWhitespace() }) return null
        val normalized = if (trimmed.startsWith("/")) "file://$trimmed" else trimmed
        if (schemeOf(normalized) !in INPUT_SCHEMES) return null
        if (validationError(normalized) != null) return null
        return PlaybackSource.of(normalized)
    }
}
