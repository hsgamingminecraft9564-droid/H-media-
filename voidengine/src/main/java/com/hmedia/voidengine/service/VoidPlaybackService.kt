package com.hmedia.voidengine.service

import android.content.Intent
import android.os.Looper
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

/**
 * Background playback + media notification + lock-screen/Bluetooth controls.
 * Requires VoidEngineHolder.engine to be set (VoidEngines.createDefault does it) and,
 * on Android 13+, the POST_NOTIFICATIONS runtime permission for the notification to show.
 * Start it by connecting with PlaybackServiceConnector.
 */
class VoidPlaybackService : MediaSessionService() {

    private var session: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val engine = VoidEngineHolder.engine
        if (engine == null) {
            stopSelf()
            return
        }
        session = MediaSession.Builder(this, VoidMediaSessionPlayer(VoidPlayer(engine, Looper.getMainLooper()))).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = session?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        super.onDestroy()
    }
}
