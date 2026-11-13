package com.hmedia.voidengine.service

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// What the notification, lock screen and headset controls show, and the next and previous actions.
// The app owns the queue; it writes here whenever the track or the queue changes.
object NowPlayingBridge {
    data class Info(
        val title: String = "",
        val artist: String = "",
        val album: String = "",
        val artwork: ByteArray? = null,
        val hasNext: Boolean = false,
        val hasPrevious: Boolean = false
    )

    private val mutableState = MutableStateFlow(Info())
    val state: StateFlow<Info> = mutableState.asStateFlow()

    @Volatile var onNext: (() -> Unit)? = null
    @Volatile var onPrevious: (() -> Unit)? = null

    // A new track starts: no artwork until the app supplies it
    fun update(title: String, artist: String, album: String, hasNext: Boolean, hasPrevious: Boolean) {
        mutableState.value = Info(title, artist, album, null, hasNext, hasPrevious)
    }

    fun setArtwork(bytes: ByteArray?) {
        mutableState.value = mutableState.value.copy(artwork = bytes)
    }

    fun setAvailability(hasNext: Boolean, hasPrevious: Boolean) {
        val current = mutableState.value
        if (current.hasNext != hasNext || current.hasPrevious != hasPrevious) {
            mutableState.value = current.copy(hasNext = hasNext, hasPrevious = hasPrevious)
        }
    }

    fun clear() {
        mutableState.value = Info()
        onNext = null
        onPrevious = null
    }
}
