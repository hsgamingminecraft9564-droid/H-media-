package com.hmedia.voidengine

import android.view.SurfaceView
import com.hmedia.voidengine.reliability.AudioGuard

class FakeBackend : BackendAdapter {
    override var listener: BackendAdapter.Listener? = null

    var setSourceCalls = 0
    var prepareCalls = 0
    var playCalls = 0
    var pauseCalls = 0
    var stopCalls = 0
    var released = false
    var lastSeek = -1L
    var lastSpeed = 1f
    var lastVolume = 1f

    override fun setSource(source: PlaybackSource) { setSourceCalls++ }
    override fun prepare() { prepareCalls++ }
    override fun play() { playCalls++ }
    override fun pause() { pauseCalls++ }
    override fun stop() { stopCalls++ }
    override fun seekTo(positionMs: Long) { lastSeek = positionMs }
    override fun setPlaybackSpeed(speed: Float) { lastSpeed = speed }
    override fun setVolume(volume: Float) { lastVolume = volume }
    override fun selectAudioTrack(id: String?) = Unit
    override fun selectSubtitleTrack(id: String?) = Unit
    override fun setEqualizer(settings: EqualizerSettings?) = Unit
    override fun equalizerBandFrequenciesHz(): List<Int> = emptyList()
    override fun attachVideoView(view: SurfaceView) = Unit
    override fun detachVideoView() = Unit
    override fun release() { released = true; listener = null }

    fun emitState(state: PlaybackState) { listener!!.onState(state) }
    fun emitError(error: PlaybackError) { listener!!.onError(error) }
}

class FakeGuard : AudioGuard {
    override var callback: AudioGuard.Callback? = null
    var acquireResult = true
    var acquires = 0
    var releases = 0
    override fun acquire(): Boolean { acquires++; return acquireResult }
    override fun release() { releases++ }
}
