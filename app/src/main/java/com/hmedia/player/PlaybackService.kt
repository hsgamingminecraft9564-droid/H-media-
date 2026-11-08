package com.hmedia.player

import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.IBinder
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

// Keeps playback alive in the background: a foreground service with a media notification
// and lock-screen controls. The music itself lives in Playback.
class PlaybackService : Service() {
    companion object {
        private const val CHANNEL_ID = "hmedia_playback"
        private const val NOTIFICATION_ID = 1
        private const val ACTION_TOGGLE = "com.hmedia.player.action.TOGGLE"
        private const val ACTION_STOP = "com.hmedia.player.action.STOP"

        var instance: PlaybackService? = null
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, PlaybackService::class.java))
        }

        // Only while something is loaded, so a stopped service is never put back in the foreground
        fun refresh() {
            instance?.let {
                if (Playback.hasEngine) {
                    it.updateSession()
                    it.showNotification()
                }
            }
        }
    }

    private lateinit var session: MediaSessionCompat

    // Headphones unplugged: pause, as every music app does
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) Playback.pause()
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        NotificationManagerCompat.from(this).createNotificationChannel(
            NotificationChannelCompat.Builder(CHANNEL_ID, NotificationManagerCompat.IMPORTANCE_LOW)
                .setName(getString(R.string.playback_channel))
                .build()
        )
        session = MediaSessionCompat(this, "HMediaPlayback").apply {
            setCallback(object : MediaSessionCompat.Callback() {
                override fun onPlay() = Playback.resume()
                override fun onPause() = Playback.pause()
                override fun onStop() = Playback.stop()
                override fun onSeekTo(pos: Long) = Playback.seekTo(pos)
            })
            isActive = true
        }
        ContextCompat.registerReceiver(
            this, noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE -> Playback.toggle()
            ACTION_STOP -> {
                Playback.stop()
                return START_NOT_STICKY
            }
        }
        updateSession()
        showNotification()
        if (!Playback.hasEngine) shutdown()
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // The user closed the app from the recent list: stop, so nothing plays without a way to stop it
    override fun onTaskRemoved(rootIntent: Intent?) {
        Playback.stop()
        super.onTaskRemoved(rootIntent)
    }

    fun shutdown() {
        stopForeground(ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun showNotification() {
        val playing = Playback.isPlaying()
        val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        val openApp = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java), flags)
        val toggle = PendingIntent.getService(
            this, 1, Intent(this, PlaybackService::class.java).setAction(ACTION_TOGGLE), flags
        )
        val stop = PendingIntent.getService(
            this, 2, Intent(this, PlaybackService::class.java).setAction(ACTION_STOP), flags
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(Playback.title.ifEmpty { getString(R.string.app_name) })
            .setContentText(getString(if (playing) R.string.now_playing else R.string.paused))
            .setContentIntent(openApp)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play,
                getString(if (playing) R.string.pause else R.string.play),
                toggle
            )
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, getString(R.string.stop), stop)
            .setStyle(
                androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.sessionToken)
                    .setShowActionsInCompactView(0, 1)
            )
            .build()
        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        )
    }

    private fun updateSession() {
        val state = if (Playback.isPlaying()) PlaybackStateCompat.STATE_PLAYING else PlaybackStateCompat.STATE_PAUSED
        session.setPlaybackState(
            PlaybackStateCompat.Builder()
                .setState(state, Playback.engine?.positionMs() ?: 0L, 1f)
                .setActions(
                    PlaybackStateCompat.ACTION_PLAY or PlaybackStateCompat.ACTION_PAUSE or
                        PlaybackStateCompat.ACTION_PLAY_PAUSE or PlaybackStateCompat.ACTION_STOP or
                        PlaybackStateCompat.ACTION_SEEK_TO
                )
                .build()
        )
        session.setMetadata(
            MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, Playback.title)
                .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, Playback.engine?.durationMs() ?: 0L)
                .build()
        )
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(noisyReceiver) }
        session.isActive = false
        session.release()
        instance = null
        super.onDestroy()
    }
}
