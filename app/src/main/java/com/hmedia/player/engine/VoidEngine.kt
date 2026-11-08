package com.hmedia.player.engine

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout

// Void engine: libVLC playback with a preset, a progress watchdog, a weak-network boost,
// reconnects, a software-decoding fallback, and a session report for the learner.
class VoidEngine(
    private val context: Context,
    private val preset: VoidPreset,
    private val learnKey: String,        // e.g. "WIFI|video"
    val kind: String,                    // "video", "audio" or "stream"
    private val onSession: (SessionStats) -> Unit = {},
    private val onStatus: (String) -> Unit = {}
) {
    private var activePreset = preset
    private var libVlc: LibVLC = newLibVlc(preset)
    private var player: MediaPlayer = newPlayer(libVlc)
    private var layout: VLCVideoLayout? = null
    private val handler = Handler(Looper.getMainLooper())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var pausedByFocus = false
    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                pausedByFocus = false
                userPaused = true
                pause()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                if (!released && player.isPlaying) {
                    pausedByFocus = true
                    player.pause()
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (pausedByFocus && !released) {
                    pausedByFocus = false
                    player.play()
                }
            }
        }
    }

    private var uri: Uri? = null
    private var retries = 0
    private var boosts = 0
    private var boostOptions: List<String> = emptyList()
    private var fellBack = false
    private var gaveUp = false
    private var finished = false
    private var reconnectTask: Runnable? = null   // at most one retry waits at a time
    private var needsReload = false              // the last media failed and must be opened again
    private var userPaused = false
    private var released = false

    private var openedAt = 0L
    private var startupMs = -1L          // set once, at the first Playing event
    private var playingSince = 0L        // 0 when not playing
    private var playedMs = 0L
    private var lastTime = -1L           // last playback position seen, in ms
    private var lastProgressAt = 0L
    private var stalls = 0
    private var errors = 0
    private var wasBuffering = false
    private val recentStalls = ArrayDeque<Long>()

    var onPlayingChanged: ((Boolean) -> Unit)? = null

    private val watchdog = object : Runnable {
        override fun run() {
            if (released) return
            checkProgress()
            handler.postDelayed(this, WATCH_TICK_MS)
        }
    }

    private val focusRequest: AudioFocusRequest? =
        if (Build.VERSION.SDK_INT >= 26) {
            AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener(focusListener, handler)
                .build()
        } else {
            null
        }

    private fun newLibVlc(p: VoidPreset): LibVLC =
        LibVLC(context.applicationContext, ArrayList(p.libOptions))

    private fun newPlayer(vlc: LibVLC): MediaPlayer {
        val p = MediaPlayer(vlc)
        // Events from a player that was already replaced are ignored
        p.setEventListener { event -> handler.post { if (p === player) onEvent(event) } }
        return p
    }

    fun open(target: Uri) {
        uri = target
        finished = false
        cancelReconnect()
        retries = 0
        boosts = 0
        boostOptions = emptyList()
        fellBack = false
        gaveUp = false
        openedAt = SystemClock.elapsedRealtime()
        handler.removeCallbacks(watchdog)
        handler.post(watchdog)
        requestFocus()
        playFrom(target, 0L)
    }

    fun attach(view: VLCVideoLayout) {
        layout = view
        player.attachViews(view, null, true, false)
    }

    fun toggle() {
        if (released) return
        if (finished) {
            finished = false
            userPaused = false
            requestFocus()
            uri?.let { playFrom(it, 0L) }
            return
        }
        if (gaveUp || needsReload || reconnectTask != null) {
            // A retry was waiting, or the last attempt failed: start the stream again from its live point
            gaveUp = false
            retries = 0
            userPaused = false
            cancelReconnect()
            requestFocus()
            uri?.let { playFrom(it, lastTime.coerceAtLeast(0L)) }
            return
        }
        if (player.isPlaying) {
            userPaused = true
            cancelReconnect()
            player.pause()
        } else {
            userPaused = false
            requestFocus()
            player.play()
        }
    }

    // Used by the screen when the app goes to the background, and by audio focus loss.
    // Pausing also cancels any retry that is waiting, so it cannot start playback later.
    fun pause() {
        if (released) return
        cancelReconnect()
        if (player.isPlaying) player.pause()
    }

    fun isPlayingNow(): Boolean = !released && player.isPlaying

    // True when the last known position is within five seconds of the end
    private fun nearEnd(): Boolean {
        val len = player.length
        return len > 0 && lastTime >= len - 5_000L
    }

    // Removes the picture when the player screen closes. The sound keeps going.
    fun detachVideo() {
        if (released) return
        player.detachViews()
        layout = null
    }

    fun positionMs(): Long = if (released) 0L else player.time.coerceAtLeast(0L)

    fun durationMs(): Long = if (released) 0L else player.length.coerceAtLeast(0L)

    fun seekTo(ms: Long) {
        if (!released && kind != "stream") player.time = ms.coerceAtLeast(0L)
    }

    @Suppress("DEPRECATION")
    private fun requestFocus() {
        val request = focusRequest
        if (request != null) {
            audioManager.requestAudioFocus(request)
        } else {
            legacyRequestFocus()
        }
    }

    private fun abandonFocus() {
        val request = focusRequest
        if (request != null) {
            audioManager.abandonAudioFocusRequest(request)
        } else {
            legacyAbandonFocus()
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyRequestFocus() {
        audioManager.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
    }

    @Suppress("DEPRECATION")
    private fun legacyAbandonFocus() {
        audioManager.abandonAudioFocus(focusListener)
    }

    // Starts the current media from a position. Files resume there; live streams start at the live point.
    private fun playFrom(target: Uri, resumeMs: Long) {
        pausePlay()
        needsReload = false
        val media = Media(libVlc, target)
        (activePreset.mediaOptions + boostOptions).forEach { media.addOption(it) }
        if (kind != "stream" && resumeMs > 1000L) media.addOption(":start-time=${resumeMs / 1000L}")
        player.setMedia(media)
        media.release()
        lastTime = -1L
        lastProgressAt = SystemClock.elapsedRealtime()
        player.play()
    }

    private fun onEvent(event: MediaPlayer.Event) {
        if (released) return // late events after release must not restart the notification
        when (event.type) {
            MediaPlayer.Event.Playing -> {
                finished = false
                wasBuffering = false
                val now = SystemClock.elapsedRealtime()
                if (startupMs < 0) startupMs = now - openedAt
                lastTime = -1L
                lastProgressAt = now
                if (playingSince == 0L) playingSince = now
                onPlayingChanged?.invoke(true)
            }
            MediaPlayer.Event.Paused, MediaPlayer.Event.Stopped -> {
                pausePlay()
                onPlayingChanged?.invoke(false)
            }
            MediaPlayer.Event.Buffering -> onBuffering(event.buffering)
            MediaPlayer.Event.EncounteredError -> {
                errors++
                pausePlay()
                onPlayingChanged?.invoke(false)
                onFailure()
            }
            MediaPlayer.Event.EndReached -> {
                pausePlay()
                onPlayingChanged?.invoke(false)
                // A file that reached its end is finished. A network stream that stops early is reconnected.
                if (!isNetwork(uri) || nearEnd()) {
                    finished = true
                    onStatus("Finished. Press Play to play again.")
                } else {
                    reconnect()
                }
            }
        }
    }

    private fun onBuffering(percent: Float) {
        if (percent >= 100f) {
            wasBuffering = false
            return
        }
        if (playingSince == 0L || wasBuffering) return
        wasBuffering = true
        onStall()
    }

    // Counts a stall. Returns true if it started a weak-network boost (which already reopens the stream).
    private fun onStall(): Boolean {
        stalls++
        val now = SystemClock.elapsedRealtime()
        recentStalls.addLast(now)
        while (recentStalls.isNotEmpty() && now - recentStalls.first() > BOOST_WINDOW_MS) {
            recentStalls.removeFirst()
        }
        if (recentStalls.size >= BOOST_STALLS && boosts < MAX_BOOSTS) {
            boost()
            return true
        }
        return false
    }

    // First boost: a much bigger buffer. Second boost: audio only, so sound keeps playing.
    private fun boost() {
        val target = uri ?: return
        boosts++
        recentStalls.clear()
        val cache = listOf(":network-caching=15000", ":live-caching=15000")
        boostOptions = if (boosts == 1) cache else cache + ":no-video"
        onStatus(if (boosts == 1) "Weak network: bigger buffer" else "Weak network: audio only to keep playing")
        playFrom(target, player.time)
    }

    // Catches freezes libVLC never reports, and playback that never starts
    private fun checkProgress() {
        if (gaveUp) return
        val now = SystemClock.elapsedRealtime()
        if (playingSince != 0L && ReconnectPolicy.clearRetriesNow(retries, now - playingSince)) retries = 0
        if (playingSince == 0L) {
            if (startupMs < 0 && !userPaused && now - openedAt > STARTUP_MS) {
                openedAt = now
                errors++
                if (isNetwork(uri)) reconnect() else onFailure()
            }
            return
        }
        val t = player.time
        if (t < 0) { // position not known yet, so there is nothing to judge
            lastProgressAt = now
            return
        }
        if (t != lastTime) {
            lastTime = t
            lastProgressAt = now
            return
        }
        if (now - lastProgressAt > FREEZE_MS) {
            lastProgressAt = now
            if (!onStall()) {
                onStatus("Playback froze. Restarting at the same spot.")
                if (isNetwork(uri)) reconnect() else uri?.let { playFrom(it, player.time) }
            }
        }
    }

    // A hardware decoder failure gets one retry in software. Streams then reconnect.
    // Anything else gets a plain message.
    private fun onFailure() {
        if (!fellBack && activePreset.libOptions.contains(HARDWARE)) {
            fellBack = true
            onStatus("Switching to software decoding")
            switchPreset(VoidPresets.byId("software"))
            return
        }
        if (isNetwork(uri)) {
            reconnect()
        } else {
            onStatus("This file could not be played. Its format may not be supported here.")
            gaveUp = true
        }
    }

    // Replaces the decoder stack with a new preset and continues from the same spot
    private fun switchPreset(p: VoidPreset) {
        val target = uri ?: return
        cancelReconnect()
        val resumeMs = player.time
        pausePlay()
        player.stop()
        player.detachViews()
        player.release()
        libVlc.release()
        activePreset = p
        libVlc = newLibVlc(p)
        player = newPlayer(libVlc)
        layout?.let { player.attachViews(it, null, true, false) }
        playFrom(target, resumeMs.coerceAtLeast(0L))
    }

    // Waits a little longer after each failed try, so a dropped stream returns without hammering the server
    private fun reconnect() {
        val target = uri ?: return
        pausePlay()
        when (ReconnectPolicy.decide(isNetwork(target), released, userPaused, nearEnd = false, attemptsSoFar = retries)) {
            ReconnectPolicy.Decision.NONE -> return
            ReconnectPolicy.Decision.GIVE_UP -> {
                gaveUp = true
                onStatus("Connection lost. Tap Play to try again.")
                return
            }
            ReconnectPolicy.Decision.RECONNECT -> Unit
        }
        retries++
        val resumeAt = lastTime
        needsReload = true
        cancelReconnect()
        // The retry checks again when it fires: a released engine or a user pause cancels it
        val task = Runnable {
            reconnectTask = null
            if (!released && !userPaused) playFrom(target, resumeAt.coerceAtLeast(0L))
        }
        reconnectTask = task
        handler.postDelayed(task, ReconnectPolicy.delayMs(retries))
    }

    private fun cancelReconnect() {
        reconnectTask?.let { handler.removeCallbacks(it) }
        reconnectTask = null
    }

    fun release() {
        released = true
        onPlayingChanged = null
        abandonFocus()
        pausePlay()
        handler.removeCallbacksAndMessages(null)
        player.stop()
        player.detachViews()
        player.release()
        libVlc.release()
        val startupSec = if (startupMs >= 0) startupMs / 1000.0 else 0.0
        onSession(SessionStats(preset.id, learnKey, playedMs / 1000.0, stalls, errors, startupSec))
    }

    private fun pausePlay() {
        if (playingSince != 0L) {
            playedMs += SystemClock.elapsedRealtime() - playingSince
            playingSince = 0L
        }
    }

    // One shared list of protocols, in MediaClassifier, so every part of the app agrees
    private fun isNetwork(u: Uri?): Boolean = MediaClassifier.isNetwork(u?.scheme)

    companion object {
        const val BOOST_WINDOW_MS = 60_000L
        const val BOOST_STALLS = 3
        const val MAX_BOOSTS = 2
        const val FREEZE_MS = 12_000L
        const val STARTUP_MS = 25_000L
        const val WATCH_TICK_MS = 1_000L
        const val HARDWARE = "--avcodec-hw=any"
    }
}
