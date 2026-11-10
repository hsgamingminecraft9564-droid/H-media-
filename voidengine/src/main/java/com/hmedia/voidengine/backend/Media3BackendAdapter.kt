package com.hmedia.voidengine.backend

import android.content.Context
import android.media.audiofx.Equalizer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.SurfaceView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource as ExoMediaSource
import com.hmedia.voidengine.BackendAdapter
import com.hmedia.voidengine.EqualizerSettings
import com.hmedia.voidengine.PlaybackError
import com.hmedia.voidengine.PlaybackSource
import com.hmedia.voidengine.PlaybackState
import com.hmedia.voidengine.SourceType
import com.hmedia.voidengine.TrackInfo

/**
 * Media3 / ExoPlayer backend. Created and called on the main thread (the engine guarantees that).
 * Audio focus and "becoming noisy" are DISABLED here on purpose: the engine's AudioGuard owns them.
 *
 * [vaultDataSourceFactory]: pass the factory from PrivateVault to play vault:// sources.
 */
class Media3BackendAdapter(
    context: Context,
    private val vaultDataSourceFactory: DataSource.Factory? = null
) : BackendAdapter {

    private val app: Context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())

    override var listener: BackendAdapter.Listener? = null

    private var player: ExoPlayer? = ExoPlayer.Builder(app)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ false
        )
        .setHandleAudioBecomingNoisy(false)
        .setWakeMode(C.WAKE_MODE_NETWORK)
        .build()

    private var currentSource: PlaybackSource? = null
    private var userStarted = false
    private var attachedView: SurfaceView? = null

    private var equalizer: Equalizer? = null
    private var equalizerRequest: EqualizerSettings? = null

    private val ticker = object : Runnable {
        override fun run() {
            publishPosition()
            handler.postDelayed(this, POSITION_INTERVAL_MS)
        }
    }

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) = publishState()

        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = publishState()

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            if (isPlaying) startTicker() else stopTicker()
            publishPosition()
        }

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int
        ) = publishPosition()

        override fun onTracksChanged(tracks: Tracks) = publishTracks(tracks)

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            if (videoSize.width > 0 && videoSize.height > 0) {
                listener?.onVideoSize(videoSize.width, videoSize.height)
            }
        }

        override fun onAudioSessionIdChanged(audioSessionId: Int) = rebuildEqualizer(audioSessionId)

        override fun onPlayerError(error: PlaybackException) = handleError(error)
    }

    init {
        val p = player
        if (p != null) {
            p.addListener(playerListener)
            rebuildEqualizer(p.audioSessionId)
        }
    }

    // ------------------------------------------------------------------ BackendAdapter

    override fun setSource(source: PlaybackSource) {
        val p = player ?: return
        currentSource = source
        userStarted = false
        p.playWhenReady = false
        p.setMediaSource(buildMediaSource(source), source.startPositionMs.coerceAtLeast(0L))
    }

    override fun prepare() {
        val p = player ?: return
        val source = currentSource
        if (p.mediaItemCount == 0 && source != null) {
            p.setMediaSource(buildMediaSource(source), source.startPositionMs.coerceAtLeast(0L))
        }
        p.prepare()
    }

    override fun play() {
        userStarted = true
        player?.play()
    }

    override fun pause() {
        player?.pause()
    }

    override fun stop() {
        val p = player ?: return
        userStarted = false
        stopTicker()
        p.playWhenReady = false
        p.stop()
        p.clearMediaItems()
    }

    override fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    override fun setPlaybackSpeed(speed: Float) {
        player?.setPlaybackSpeed(speed)
    }

    override fun setVolume(volume: Float) {
        player?.volume = volume.coerceIn(0f, 1f)
    }

    override fun selectAudioTrack(id: String?) = selectTrack(C.TRACK_TYPE_AUDIO, id)

    override fun selectSubtitleTrack(id: String?) = selectTrack(C.TRACK_TYPE_TEXT, id)

    override fun setEqualizer(settings: EqualizerSettings?) {
        equalizerRequest = settings
        applyEqualizer()
    }

    override fun equalizerBandFrequenciesHz(): List<Int> {
        val eq = equalizer ?: return emptyList()
        return (0 until eq.numberOfBands.toInt()).map { band -> eq.getCenterFreq(band.toShort()) / 1000 }
    }

    override fun attachVideoView(view: SurfaceView) {
        val p = player ?: return
        if (attachedView === view) return
        p.setVideoSurfaceView(view)
        attachedView = view
    }

    override fun detachVideoView() {
        val view = attachedView ?: return
        player?.clearVideoSurfaceView(view)
        attachedView = null
    }

    override fun release() {
        listener = null
        stopTicker()
        releaseEqualizer()
        val p = player
        player = null
        if (p != null) {
            p.removeListener(playerListener)
            p.release()
        }
        attachedView = null
    }

    // ------------------------------------------------------------------ internals

    private fun buildMediaSource(source: PlaybackSource): ExoMediaSource {
        val uri = Uri.parse(source.uri)
        val itemBuilder = MediaItem.Builder()
            .setUri(uri)
            .setMediaMetadata(MediaMetadata.Builder().setTitle(source.title).build())
        when (source.type) {
            SourceType.HLS -> itemBuilder.setMimeType(MimeTypes.APPLICATION_M3U8)
            SourceType.DASH -> itemBuilder.setMimeType(MimeTypes.APPLICATION_MPD)
            else -> Unit
        }
        if (source.externalSubtitles.isNotEmpty()) {
            itemBuilder.setSubtitleConfigurations(
                source.externalSubtitles.map { subtitle ->
                    MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitle.uri))
                        .setMimeType(subtitle.mimeType)
                        .setLanguage(subtitle.language)
                        .setLabel(subtitle.label)
                        .build()
                }
            )
        }
        val dataSourceFactory: DataSource.Factory =
            if (uri.scheme == "vault" && vaultDataSourceFactory != null) {
                vaultDataSourceFactory
            } else {
                val http = DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setDefaultRequestProperties(source.headers)
                DefaultDataSource.Factory(app, http)
            }
        return DefaultMediaSourceFactory(dataSourceFactory).createMediaSource(itemBuilder.build())
    }

    private fun publishState() {
        val p = player ?: return
        val state: PlaybackState? = when (p.playbackState) {
            Player.STATE_BUFFERING -> PlaybackState.BUFFERING
            Player.STATE_READY -> when {
                !userStarted -> PlaybackState.READY
                p.playWhenReady -> PlaybackState.PLAYING
                else -> PlaybackState.PAUSED
            }
            Player.STATE_ENDED -> PlaybackState.ENDED
            else -> null // STATE_IDLE: the engine owns IDLE
        }
        if (state != null) listener?.onState(state)
    }

    private fun publishPosition() {
        val p = player ?: return
        val duration = if (p.duration == C.TIME_UNSET) -1L else p.duration
        listener?.onPosition(p.currentPosition.coerceAtLeast(0L), duration)
        listener?.onBuffering(p.bufferedPercentage)
    }

    private fun startTicker() {
        handler.removeCallbacks(ticker)
        handler.post(ticker)
    }

    private fun stopTicker() {
        handler.removeCallbacks(ticker)
    }

    private fun publishTracks(tracks: Tracks) {
        val audio = ArrayList<TrackInfo>()
        val subtitles = ArrayList<TrackInfo>()
        var selectedAudio: String? = null
        var selectedSubtitle: String? = null
        tracks.groups.forEachIndexed { groupIndex, group ->
            val type = group.type
            if (type != C.TRACK_TYPE_AUDIO && type != C.TRACK_TYPE_TEXT) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                val id = "$groupIndex:$trackIndex"
                val info = TrackInfo(id, format.label ?: format.language ?: "Track ${trackIndex + 1}", format.language)
                if (type == C.TRACK_TYPE_AUDIO) {
                    audio.add(info)
                    if (group.isTrackSelected(trackIndex)) selectedAudio = id
                } else {
                    subtitles.add(info)
                    if (group.isTrackSelected(trackIndex)) selectedSubtitle = id
                }
            }
        }
        listener?.onTracks(audio, subtitles, selectedAudio, selectedSubtitle)
    }

    private fun selectTrack(type: Int, id: String?) {
        val p = player ?: return
        val params = p.trackSelectionParameters.buildUpon()
        if (id == null) {
            if (type == C.TRACK_TYPE_TEXT) params.setTrackTypeDisabled(type, true) else params.clearOverridesOfType(type)
        } else {
            val parts = id.split(":")
            val groupIndex = parts.getOrNull(0)?.toIntOrNull()
            val trackIndex = parts.getOrNull(1)?.toIntOrNull()
            if (groupIndex == null || trackIndex == null) return
            val group = p.currentTracks.groups.getOrNull(groupIndex) ?: return
            if (group.type != type || trackIndex !in 0 until group.length) return
            params.setTrackTypeDisabled(type, false)
            params.setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, trackIndex))
        }
        p.trackSelectionParameters = params.build()
    }

    private fun handleError(error: PlaybackException) {
        val p = player
        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW && p != null) {
            p.seekToDefaultPosition()
            p.prepare()
            return
        }
        listener?.onError(mapError(error))
    }

    private fun mapError(error: PlaybackException): PlaybackError {
        val text = error.message ?: "Playback error"
        val cause = error.cause
        if (cause is HttpDataSource.InvalidResponseCodeException) {
            val code = cause.responseCode
            return when (code) {
                401, 403, 407 -> PlaybackError.Authentication(text, error)
                404, 410 -> PlaybackError.SourceNotFound(text, error)
                else -> PlaybackError.HttpStatus(code, text, error)
            }
        }
        return when (error.errorCode) {
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackError.Network(text, error)

            PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS -> PlaybackError.HttpStatus(0, text, error)

            PlaybackException.ERROR_CODE_IO_CLEARTEXT_NOT_PERMITTED,
            PlaybackException.ERROR_CODE_IO_INVALID_HTTP_CONTENT_TYPE -> PlaybackError.InvalidSource(text, error)

            PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND,
            PlaybackException.ERROR_CODE_IO_NO_PERMISSION -> PlaybackError.SourceNotFound(text, error)

            PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_UNSUPPORTED,
            PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES -> PlaybackError.UnsupportedFormat(text, error)

            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FAILED -> PlaybackError.Decoder(text, error)

            else -> PlaybackError.Backend(text, error)
        }
    }

    private fun rebuildEqualizer(sessionId: Int) {
        releaseEqualizer()
        if (sessionId == C.AUDIO_SESSION_ID_UNSET) return
        equalizer = try {
            Equalizer(0, sessionId)
        } catch (e: RuntimeException) {
            null // not available on this device
        }
        applyEqualizer()
    }

    private fun applyEqualizer() {
        val eq = equalizer ?: return
        val request = equalizerRequest
        try {
            if (request == null || !request.enabled) {
                eq.setEnabled(false)
                return
            }
            val range = eq.bandLevelRange
            val minLevel = range[0].toInt()
            val maxLevel = range[1].toInt()
            val bands = eq.numberOfBands.toInt()
            for (i in 0 until minOf(bands, request.bandGainsDb.size)) {
                val millibels = ((request.bandGainsDb[i] + request.preAmpDb) * 100f).toInt().coerceIn(minLevel, maxLevel)
                eq.setBandLevel(i.toShort(), millibels.toShort())
            }
            eq.setEnabled(true)
        } catch (e: RuntimeException) {
            // effect died (e.g. audio server restart); the next session change rebuilds it
        }
    }

    private fun releaseEqualizer() {
        try {
            equalizer?.release()
        } catch (e: RuntimeException) {
            // ignore
        }
        equalizer = null
    }

    private companion object {
        const val POSITION_INTERVAL_MS = 500L
    }
}
