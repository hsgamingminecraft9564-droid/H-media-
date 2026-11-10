package com.hmedia.voidengine

import android.view.SurfaceView

object BackendNames {
    const val MEDIA3 = "media3"
    const val VLC = "vlc"
    const val NATIVE = "void-native"
}

data class BackendCapabilities(
    val name: String,
    val supportedTypes: Set<SourceType>,
    val features: Set<Feature>,
    /** URI schemes this backend can open. null = any scheme. */
    val supportedSchemes: Set<String>? = null,
    /** File extensions (lower-case, no dot) this backend is best at; used only for ordering. */
    val preferredExtensions: Set<String> = emptySet()
)

/** A backend = its declared capabilities + a factory. The list order given to the engine is the priority order. */
class BackendProvider(
    val capabilities: BackendCapabilities,
    val create: () -> BackendAdapter
)

/**
 * Contract every backend (VLC, Media3, future native) must follow.
 *
 * Rules:
 *  - No VLC/Media3 types appear here.
 *  - The engine calls these methods from the MAIN thread only, never concurrently.
 *  - Callbacks on [Listener] may come from any thread.
 *  - Never report IDLE; the engine owns IDLE. Report errors only through [Listener.onError].
 *  - play() may be called before the backend is ready; it must start as soon as it is.
 *  - After stop(), a later setSource()/prepare()/play() must work again.
 *  - attachVideoView()/detachVideoView()/setEqualizer() must be idempotent.
 */
interface BackendAdapter {
    interface Listener {
        fun onState(state: PlaybackState)
        fun onPosition(positionMs: Long, durationMs: Long)
        fun onBuffering(percent: Int)
        fun onVideoSize(width: Int, height: Int)
        fun onTracks(
            audio: List<TrackInfo>,
            subtitles: List<TrackInfo>,
            selectedAudioId: String?,
            selectedSubtitleId: String?
        )
        fun onError(error: PlaybackError)
    }

    var listener: Listener?

    fun setSource(source: PlaybackSource)
    fun prepare()
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    /** 0f..1f. */
    fun setVolume(volume: Float)
    fun selectAudioTrack(id: String?)
    fun selectSubtitleTrack(id: String?)
    fun setEqualizer(settings: EqualizerSettings?)
    /** Empty list = equalizer not available on this backend/device. */
    fun equalizerBandFrequenciesHz(): List<Int>
    fun attachVideoView(view: SurfaceView)
    fun detachVideoView()
    fun release()
}
