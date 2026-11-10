package com.hmedia.voidengine.service

import android.content.ComponentName
import android.content.Context
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture

/**
 * Connecting a MediaController is what starts VoidPlaybackService and lets it post the media notification.
 * Call connect() when the player screen opens (or app starts) and disconnect() when you no longer need the service.
 */
class PlaybackServiceConnector(context: Context) {
    private val appContext: Context = context.applicationContext
    private var future: ListenableFuture<MediaController>? = null

    fun connect() {
        if (future != null) return
        val token = SessionToken(appContext, ComponentName(appContext, VoidPlaybackService::class.java))
        future = MediaController.Builder(appContext, token).buildAsync()
    }

    fun disconnect() {
        future?.let { MediaController.releaseFuture(it) }
        future = null
    }
}
