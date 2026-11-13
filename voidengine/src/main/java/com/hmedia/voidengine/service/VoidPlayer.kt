package com.hmedia.voidengine.service

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.hmedia.voidengine.PlaybackState
import com.hmedia.voidengine.VoidEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Presents a VoidEngine as a Media3 Player so MediaSession gives you the notification, lock-screen
 * controls, Bluetooth/headset buttons and Android Auto for free. It owns no playback logic.
 */
class VoidPlayer(
    private val engine: VoidEngine,
    looper: Looper
) : SimpleBasePlayer(looper) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
        scope.launch {
            engine.snapshot.collect { invalidateState() }
        }
        // Track details and next/previous availability come from the app, so refresh when they change
        scope.launch {
            NowPlayingBridge.state.collect { invalidateState() }
        }
    }

    override fun getState(): State {
        val s = engine.snapshot.value
        val seekable = s.durationMs > 0

        val commands = Player.Commands.Builder()
            .addAll(
                Player.COMMAND_PLAY_PAUSE,
                Player.COMMAND_PREPARE,
                Player.COMMAND_STOP,
                Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
                Player.COMMAND_GET_TIMELINE,
                Player.COMMAND_GET_METADATA,
                Player.COMMAND_SET_SPEED_AND_PITCH
            )
            .also { if (seekable) it.add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) }
            .build()

        val builder = State.Builder().setAvailableCommands(commands)

        val source = s.source
        if (source != null) {
            val now = NowPlayingBridge.state.value
            val metadata = MediaMetadata.Builder()
                .setTitle(now.title.takeIf { it.isNotEmpty() } ?: source.title)
                .setArtist(now.artist.takeIf { it.isNotEmpty() })
                .setAlbumTitle(now.album.takeIf { it.isNotEmpty() })
                .apply {
                    now.artwork?.let { setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER) }
                }
                .build()
            val item = MediaItemData.Builder(source.uri)
                .setMediaItem(
                    MediaItem.Builder()
                        .setMediaId(source.uri)
                        .setMediaMetadata(metadata)
                        .build()
                )
                .setDurationUs(if (seekable) s.durationMs * 1000L else C.TIME_UNSET)
                .setIsSeekable(seekable)
                .setIsDynamic(!seekable)
                .build()
            builder.setPlaylist(listOf(item))
            builder.setCurrentMediaItemIndex(0)
            builder.setContentPositionMs(s.positionMs)
        }

        builder.setPlaybackState(
            when (s.state) {
                PlaybackState.PREPARING, PlaybackState.BUFFERING -> Player.STATE_BUFFERING
                PlaybackState.READY, PlaybackState.PLAYING, PlaybackState.PAUSED -> Player.STATE_READY
                PlaybackState.ENDED -> Player.STATE_ENDED
                PlaybackState.IDLE, PlaybackState.ERROR, PlaybackState.RELEASED -> Player.STATE_IDLE
            }
        )
        builder.setPlayWhenReady(s.playWhenReady, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
        builder.setPlaybackParameters(PlaybackParameters(s.speed))

        val error = s.error
        if (error != null) {
            builder.setPlayerError(PlaybackException(error.message, error.cause, PlaybackException.ERROR_CODE_UNSPECIFIED))
        }
        return builder.build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) engine.play() else engine.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handlePrepare(): ListenableFuture<*> {
        engine.prepare()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        engine.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        engine.seekTo(positionMs)
        return Futures.immediateVoidFuture()
    }

    override fun handleSetPlaybackParameters(playbackParameters: PlaybackParameters): ListenableFuture<*> {
        engine.setPlaybackSpeed(playbackParameters.speed)
        return Futures.immediateVoidFuture()
    }

    /** Called when the MediaSession is released. The engine itself is shared, so it is NOT released here. */
    override fun handleRelease(): ListenableFuture<*> {
        scope.cancel()
        return Futures.immediateVoidFuture()
    }
}
