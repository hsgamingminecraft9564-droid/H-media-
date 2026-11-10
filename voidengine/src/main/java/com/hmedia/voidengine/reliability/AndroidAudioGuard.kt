package com.hmedia.voidengine.reliability

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper

/**
 * Modern (API 26+) audio focus via AudioFocusRequest, plus ACTION_AUDIO_BECOMING_NOISY
 * (headphones unplugged / Bluetooth disconnected). Backends must NOT also manage focus.
 */
class AndroidAudioGuard(
    context: Context,
    private val handler: Handler = Handler(Looper.getMainLooper())
) : AudioGuard {

    private val app: Context = context.applicationContext
    private val audioManager: AudioManager = app.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    override var callback: AudioGuard.Callback? = null

    private val attributes: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                callback?.onDuck(false)
                callback?.onResumeAllowed()
            }
            AudioManager.AUDIOFOCUS_LOSS -> callback?.onPauseRequested(false)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> callback?.onPauseRequested(true)
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> callback?.onDuck(true)
        }
    }

    private var focusRequest: AudioFocusRequest? = null
    private var noisyRegistered = false

    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                callback?.onPauseRequested(false)
            }
        }
    }

    override fun acquire(): Boolean {
        val request = focusRequest ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener, handler)
            .build()
            .also { focusRequest = it }
        val granted = audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (granted) registerNoisy()
        return granted
    }

    override fun release() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        unregisterNoisy()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    private fun registerNoisy() {
        if (noisyRegistered) return
        app.registerReceiver(noisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        noisyRegistered = true
    }

    private fun unregisterNoisy() {
        if (!noisyRegistered) return
        try {
            app.unregisterReceiver(noisyReceiver)
        } catch (e: IllegalArgumentException) {
            // already unregistered
        }
        noisyRegistered = false
    }
}
