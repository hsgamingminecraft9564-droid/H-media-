package com.hmedia.voidengine

import android.content.Context
import androidx.media3.datasource.DataSource
import com.hmedia.voidengine.backend.Media3BackendAdapter
import com.hmedia.voidengine.backend.VlcBackendAdapter
import com.hmedia.voidengine.reliability.AndroidAudioGuard
import com.hmedia.voidengine.service.VoidEngineHolder

/** One-call setup: Media3 first (best for common formats and streams), VLC as the universal fallback. */
object VoidEngines {

    private val MEDIA3_EXTENSIONS = setOf(
        "mp4", "m4v", "m4a", "mp3", "aac", "flac", "ogg", "oga", "opus", "wav",
        "mkv", "webm", "ts", "m3u8", "mpd", "3gp", "amr"
    )

    private val VLC_EXTENSIONS = setOf(
        "avi", "wmv", "wma", "flv", "mpg", "mpeg", "vob", "rmvb", "rm", "divx", "asf", "mov", "ape", "dts"
    )

    private val ALL_FEATURES = setOf(
        Feature.PLAYBACK_SPEED,
        Feature.AUDIO_TRACKS,
        Feature.SUBTITLE_TRACKS,
        Feature.EXTERNAL_SUBTITLES,
        Feature.EQUALIZER,
        Feature.VIDEO
    )

    /**
     * @param vaultDataSourceFactory pass VaultDataSourceFactory(vault) to enable vault:// playback.
     * @param registerForService true = also store the engine in [VoidEngineHolder] for the playback service.
     */
    fun createDefault(
        context: Context,
        vaultDataSourceFactory: DataSource.Factory? = null,
        registerForService: Boolean = true
    ): VoidEngine {
        val app = context.applicationContext

        val media3Schemes = buildSet {
            addAll(listOf("file", "content", "http", "https", "rtsp", "asset"))
            if (vaultDataSourceFactory != null) add("vault")
        }
        val media3 = BackendProvider(
            BackendCapabilities(
                name = BackendNames.MEDIA3,
                supportedTypes = setOf(
                    SourceType.LOCAL_FILE, SourceType.HTTP, SourceType.HLS,
                    SourceType.DASH, SourceType.RTSP, SourceType.IPTV_STREAM
                ),
                features = ALL_FEATURES,
                supportedSchemes = media3Schemes,
                preferredExtensions = MEDIA3_EXTENSIONS
            )
        ) { Media3BackendAdapter(app, vaultDataSourceFactory) }

        val vlc = BackendProvider(
            BackendCapabilities(
                name = BackendNames.VLC,
                supportedTypes = SourceType.values().toSet(),
                features = ALL_FEATURES,
                supportedSchemes = setOf(
                    "file", "content", "http", "https", "rtsp", "rtsps", "rtmp", "rtp", "udp", "mms", "mmsh", "ftp", "sftp", "smb"
                ),
                preferredExtensions = VLC_EXTENSIONS
            )
        ) { VlcBackendAdapter(app) }

        val engine = VoidEngineImpl(
            providers = listOf(media3, vlc),
            audioGuard = AndroidAudioGuard(app)
        )
        if (registerForService) VoidEngineHolder.engine = engine
        return engine
    }
}
