package com.hmedia.player.engine

// What kind of media an address holds: video, audio, or a network stream.
enum class MediaKind { VIDEO, AUDIO, STREAM }

// The one place that decides which addresses are supported and what kind of media they hold.
// Every part of the app uses this, so the rules cannot drift apart. It has no Android types,
// so it can be unit tested on a computer.
object MediaClassifier {
    // Network protocols supported by the libVLC build we ship
    val NETWORK_SCHEMES: Set<String> = setOf("http", "https", "rtsp", "rtmp", "udp", "mms")

    // Phone storage: Android gives local media as content: (MediaStore) or file: addresses
    val LOCAL_SCHEMES: Set<String> = setOf("content", "file")

    private val AUDIO_EXTENSIONS = setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma")

    // Schemes are compared without case or spaces, so "HTTPS" and " https " are the same protocol
    private fun normal(scheme: String?): String? = scheme?.trim()?.lowercase()

    fun isNetwork(scheme: String?): Boolean {
        val n = normal(scheme) ?: return false
        return n in NETWORK_SCHEMES
    }

    // Only these schemes can be played. Anything else is refused with a message, never guessed at.
    fun isSupported(scheme: String?): Boolean {
        val n = normal(scheme) ?: return false
        return n in NETWORK_SCHEMES || n in LOCAL_SCHEMES
    }

    fun kind(scheme: String?, path: String?, lastSegment: String?): MediaKind {
        if (isNetwork(scheme)) return MediaKind.STREAM
        if (path?.contains("/audio/") == true) return MediaKind.AUDIO
        val ext = lastSegment?.substringAfterLast('.', "")?.lowercase().orEmpty()
        return if (ext in AUDIO_EXTENSIONS) MediaKind.AUDIO else MediaKind.VIDEO
    }
}
