package com.hmedia.voidengine

/** Every state the engine can be in. The UI only ever sees these. */
enum class PlaybackState { IDLE, PREPARING, READY, PLAYING, PAUSED, BUFFERING, ENDED, ERROR, RELEASED }

/** One immutable picture of the player, published as a StateFlow so the UI never races the engine. */
data class PlayerSnapshot(
    val state: PlaybackState = PlaybackState.IDLE,
    /** What the user (or a resumed audio-focus loss) wants: true = should be playing. */
    val playWhenReady: Boolean = false,
    val positionMs: Long = 0L,
    /** [UNKNOWN_DURATION] for live streams or before the duration is known. */
    val durationMs: Long = UNKNOWN_DURATION,
    val bufferedPercent: Int = 0,
    val speed: Float = 1f,
    val volume: Float = 1f,
    val source: PlaybackSource? = null,
    val backendName: String? = null,
    val videoWidth: Int = 0,
    val videoHeight: Int = 0,
    val audioTracks: List<TrackInfo> = emptyList(),
    val subtitleTracks: List<TrackInfo> = emptyList(),
    val selectedAudioId: String? = null,
    val selectedSubtitleId: String? = null,
    /** Centre frequencies (Hz) of the equalizer bands of the active backend; empty = no equalizer. */
    val equalizerBandsHz: List<Int> = emptyList(),
    /** 0 normally; 1..N while the engine is reconnecting a dropped stream. */
    val reconnectAttempt: Int = 0,
    val error: PlaybackError? = null
) {
    companion object {
        const val UNKNOWN_DURATION = -1L
    }
}
