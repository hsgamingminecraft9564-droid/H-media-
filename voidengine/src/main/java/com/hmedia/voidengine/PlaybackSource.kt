package com.hmedia.voidengine

import com.hmedia.voidengine.features.url.SourceClassifier

enum class SourceType {
    LOCAL_FILE, HTTP, HLS, DASH, RTSP, IPTV_STREAM,
    RTMP, RTP_UDP, MMS, FTP, NETWORK_SHARE,
    /** Not playable: the scheme/URL is not recognised. The engine rejects these instead of guessing. */
    UNKNOWN;

    val isRemote: Boolean
        get() = this != LOCAL_FILE && this != UNKNOWN
}

/** Optional capabilities a source may require from a backend (used by [BackendSelector]). */
enum class Feature { PLAYBACK_SPEED, AUDIO_TRACKS, SUBTITLE_TRACKS, EXTERNAL_SUBTITLES, EQUALIZER, VIDEO }

data class TrackInfo(val id: String, val label: String, val language: String? = null)

data class ExternalSubtitle(
    val uri: String,
    val mimeType: String? = null,
    val language: String? = null,
    val label: String? = null
)

/**
 * What to play. [uri] is a plain string (file://, content://, https://, rtsp://, vault://...)
 * so the engine API has no Android types and can be unit-tested on the JVM.
 */
data class PlaybackSource(
    val uri: String,
    val type: SourceType = SourceType.UNKNOWN,
    val title: String? = null,
    val headers: Map<String, String> = emptyMap(),
    val startPositionMs: Long = 0L,
    val requiredFeatures: Set<Feature> = emptySet(),
    val externalSubtitles: List<ExternalSubtitle> = emptyList(),
    /** Optional hint, e.g. BackendNames.VLC. Honoured first by DefaultBackendSelector. */
    val preferredBackend: String? = null
) {
    companion object {
        /** Builds a source and detects the [SourceType] from the URL. */
        fun of(
            url: String,
            title: String? = null,
            headers: Map<String, String> = emptyMap(),
            startPositionMs: Long = 0L
        ): PlaybackSource = PlaybackSource(
            uri = url,
            type = SourceClassifier.classify(url),
            title = title,
            headers = headers,
            startPositionMs = startPositionMs
        )

        fun fromUri(
            uri: android.net.Uri,
            title: String? = null,
            headers: Map<String, String> = emptyMap(),
            startPositionMs: Long = 0L
        ): PlaybackSource = of(uri.toString(), title, headers, startPositionMs)
    }
}
