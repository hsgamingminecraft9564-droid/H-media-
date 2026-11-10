package com.hmedia.voidengine

import android.view.SurfaceView
import kotlinx.coroutines.flow.StateFlow

/**
 * The ONLY thing your UI, service and features should talk to.
 * All methods may be called from any thread and return immediately; effects are applied in order
 * on the engine's (main) thread, so read results from [snapshot], not from the line after the call.
 */
interface VoidEngine {
    val snapshot: StateFlow<PlayerSnapshot>

    fun setSource(source: PlaybackSource)
    fun prepare()
    fun play()
    fun pause()
    fun stop()
    fun seekTo(positionMs: Long)
    fun setPlaybackSpeed(speed: Float)
    fun setVolume(volume: Float)
    fun selectAudioTrack(id: String?)
    fun selectSubtitleTrack(id: String?)
    fun setEqualizer(settings: EqualizerSettings?)
    /** Call detachVideoView() from onStop()/onDestroyView() before the surface goes away. */
    fun attachVideoView(view: SurfaceView)
    fun detachVideoView()
    fun release()
}

/** setSource + prepare (+ play). */
fun VoidEngine.open(source: PlaybackSource, autoPlay: Boolean = true) {
    setSource(source)
    prepare()
    if (autoPlay) play()
}

fun VoidEngine.togglePlayPause() {
    if (snapshot.value.playWhenReady) pause() else play()
}
