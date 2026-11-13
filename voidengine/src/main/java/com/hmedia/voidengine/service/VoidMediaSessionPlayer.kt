package com.hmedia.voidengine.service

import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player

// Adds next and previous to what the media session offers, so the notification, lock screen and
// headset buttons show them. The app answers them through NowPlayingBridge, because it owns the queue.
class VoidMediaSessionPlayer(player: Player) : ForwardingPlayer(player) {

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon()
            .add(Player.COMMAND_SEEK_TO_PREVIOUS)
            .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            .add(Player.COMMAND_SEEK_TO_NEXT)
            .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            .build()

    override fun isCommandAvailable(command: Int): Boolean = availableCommands.contains(command)

    override fun hasNextMediaItem(): Boolean = NowPlayingBridge.state.value.hasNext

    override fun hasPreviousMediaItem(): Boolean = NowPlayingBridge.state.value.hasPrevious

    override fun seekToNextMediaItem() {
        NowPlayingBridge.onNext?.invoke()
    }

    override fun seekToPreviousMediaItem() {
        NowPlayingBridge.onPrevious?.invoke()
    }

    override fun seekToNext() {
        seekToNextMediaItem()
    }

    override fun seekToPrevious() {
        seekToPreviousMediaItem()
    }
}
