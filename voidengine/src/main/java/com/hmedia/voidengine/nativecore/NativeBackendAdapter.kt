package com.hmedia.voidengine.nativecore

import android.view.SurfaceView
import com.hmedia.voidengine.BackendAdapter
import com.hmedia.voidengine.BackendCapabilities
import com.hmedia.voidengine.BackendNames
import com.hmedia.voidengine.BackendProvider
import com.hmedia.voidengine.EqualizerSettings
import com.hmedia.voidengine.PlaybackError
import com.hmedia.voidengine.PlaybackSource
import com.hmedia.voidengine.TrackInfo

/**
 * Phase 7 extension point for your own demuxer / MediaCodec / FFmpeg pipeline.
 * Today it is an honest placeholder: it declares NO supported source types, so the selector never
 * picks it, and if you force it via preferredBackend it reports UnsupportedFormat and the engine
 * falls back to the next backend. Replace components one at a time (see docs/PHASE7_NATIVE.md),
 * widen [provider]'s capabilities as each one passes the shared acceptance tests.
 */
class NativeBackendAdapter : BackendAdapter {
    override var listener: BackendAdapter.Listener? = null

    override fun setSource(source: PlaybackSource) {
        listener?.onError(PlaybackError.UnsupportedFormat("Native backend has no components yet"))
    }

    override fun prepare() = Unit
    override fun play() = Unit
    override fun pause() = Unit
    override fun stop() = Unit
    override fun seekTo(positionMs: Long) = Unit
    override fun setPlaybackSpeed(speed: Float) = Unit
    override fun setVolume(volume: Float) = Unit
    override fun selectAudioTrack(id: String?) = Unit
    override fun selectSubtitleTrack(id: String?) = Unit
    override fun setEqualizer(settings: EqualizerSettings?) = Unit
    override fun equalizerBandFrequenciesHz(): List<Int> = emptyList()
    override fun attachVideoView(view: SurfaceView) = Unit
    override fun detachVideoView() = Unit
    override fun release() {
        listener = null
    }

    companion object {
        fun provider(): BackendProvider = BackendProvider(
            BackendCapabilities(
                name = BackendNames.NATIVE,
                supportedTypes = emptySet(),
                features = emptySet()
            )
        ) { NativeBackendAdapter() }
    }
}
