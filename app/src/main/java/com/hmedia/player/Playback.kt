/*
 * Copyright (c) 2026 Himanshu. All rights reserved.
 */

package com.hmedia.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hmedia.voidengine.PlaybackSource
import com.hmedia.voidengine.features.equalizer.EqualizerPresets
import com.hmedia.voidengine.PlaybackState
import com.hmedia.voidengine.VoidEngine
import com.hmedia.voidengine.VoidEngines
import com.hmedia.voidengine.open
import com.hmedia.voidengine.service.PlaybackServiceConnector
import com.hmedia.voidengine.togglePlayPause
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import com.hmedia.voidengine.service.NowPlayingBridge
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private val AUDIO_EXT = setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma")

// Video, audio, or stream, decided from the address. Used for the screen layout.
fun mediaKind(uri: Uri): String {
    val scheme = uri.scheme ?: ""
    if (scheme.startsWith("http") || scheme == "rtsp" || scheme == "rtmp" || scheme == "udp") return "stream"
    val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() ?: ""
    return if (uri.path?.contains("/audio/") == true || ext in AUDIO_EXT) "audio" else "video"
}

enum class RepeatMode { OFF, ALL, ONE }

// The one playing file for the whole app, with its queue. The VOID engine plays it, and the engine's
// own background service keeps it going with the screen off.
object Playback {
    private val SUPPORTED = setOf(
        "file", "content", "http", "https", "rtsp", "rtsps", "rtmp", "rtmps",
        "rtp", "udp", "mms", "mmsh", "ftp", "sftp", "smb"
    )
    private const val RECENT_MAX = 12
    private const val PREVIOUS_RESTART_MS = 3000L

    var engine by mutableStateOf<VoidEngine?>(null)
        private set
    var title by mutableStateOf("")
        private set
    var kind by mutableStateOf("video")
        private set
    var currentUri by mutableStateOf<Uri?>(null)
        private set
    var playing by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    var shuffle by mutableStateOf(false)
        private set
    var repeat by mutableStateOf(RepeatMode.OFF)
        private set
    var playbackSpeed by mutableStateOf(1f)
        private set
    var recent by mutableStateOf<List<MediaItem>>(emptyList())
        private set

    // Sleep timer: pauses after a set time, or when the current track ends
    var sleepEndsAtMs by mutableStateOf(0L)
        private set
    var sleepAfterTrack by mutableStateOf(false)
        private set

    // A-B repeat: the part of the file between the two marks plays over and over. -1 means not set.
    var abStartMs by mutableStateOf(-1L)
        private set
    var abEndMs by mutableStateOf(-1L)
        private set

    private val sleepHandler = Handler(Looper.getMainLooper())
    private val sleepRunnable = Runnable {
        sleepEndsAtMs = 0L
        sleepAfterTrack = false
        engine?.pause()
    }
    private var lastResumeSave = 0L
    private var equalizerApplied = false

    // The saved settings, for the player tools
    val prefs: AppPrefs? get() = appPrefs

    private var queue: List<MediaItem> = emptyList()
    private var order: List<Int> = emptyList()
    private var orderPos = 0

    // True from the moment an item is seen finished until the engine reports any other state, so a
    // single finish is never handled twice (which would skip a track).
    private var endHandled = false
    private var scope: CoroutineScope? = null
    private val artworkScope = MainScope()
    private var connector: PlaybackServiceConnector? = null
    private var appContext: Context? = null
    private var appPrefs: AppPrefs? = null

    val hasEngine: Boolean get() = engine != null

    fun isPlaying(): Boolean = playing

    // Plays one file as a queue of one. Returns false if it cannot be played; the reason is in [status].
    fun play(context: Context, prefs: AppPrefs, target: Uri, name: String): Boolean {
        val item = MediaItem(title = name, uri = target, isVideo = mediaKind(target) != "audio")
        return playQueue(context, prefs, listOf(item), 0)
    }

    // Starts a queue at [startIndex]. Shuffle and repeat apply to this queue.
    fun playQueue(context: Context, prefs: AppPrefs, items: List<MediaItem>, startIndex: Int): Boolean {
        if (items.isEmpty()) {
            status = "There is nothing to play."
            return false
        }
        appContext = context.applicationContext
        appPrefs = prefs
        queue = items
        rebuildOrder(startIndex.coerceIn(0, items.size - 1))
        return loadCurrent()
    }

    // Plays the list in random order with shuffle switched on
    fun playQueueShuffled(context: Context, prefs: AppPrefs, items: List<MediaItem>): Boolean {
        if (items.isEmpty()) {
            status = "There is nothing to play."
            return false
        }
        shuffle = true
        return playQueue(context, prefs, items, items.indices.random())
    }

    fun toggle() {
        engine?.togglePlayPause()
    }

    fun pause() {
        engine?.pause()
    }

    fun resume() {
        engine?.let { if (!it.snapshot.value.playWhenReady) it.play() }
    }

    fun seekTo(ms: Long) {
        engine?.seekTo(ms)
    }

    fun startSleepTimer(minutes: Int) {
        cancelSleep()
        val delayMs = minutes * 60_000L
        sleepEndsAtMs = SystemClock.elapsedRealtime() + delayMs
        sleepHandler.postDelayed(sleepRunnable, delayMs)
    }

    fun sleepAfterCurrentTrack() {
        cancelSleep()
        sleepAfterTrack = true
    }

    fun cancelSleep() {
        sleepHandler.removeCallbacks(sleepRunnable)
        sleepEndsAtMs = 0L
        sleepAfterTrack = false
    }

    private fun positionNow(): Long = engine?.snapshot?.value?.positionMs ?: 0L

    // Marks the start of the repeated part at the current time
    fun markAbStart() {
        val position = positionNow()
        abStartMs = position
        if (abEndMs in 0..position) abEndMs = -1L
    }

    // Marks the end of the repeated part at the current time; the start is the beginning if it was not set
    fun markAbEnd() {
        val position = positionNow()
        if (abStartMs < 0L) abStartMs = 0L
        if (position > abStartMs + 500L) abEndMs = position
    }

    fun clearAb() {
        abStartMs = -1L
        abEndMs = -1L
    }

    // Applies the saved equalizer preset to the playing file, when its backend has an equalizer
    fun applyEqualizer() {
        val e = engine ?: return
        val bands = e.snapshot.value.equalizerBandsHz.size
        if (bands == 0) return
        e.setEqualizer(EqualizerPresets.forName(appPrefs?.eqPreset ?: EqualizerPresets.FLAT, bands))
        equalizerApplied = true
    }

    fun chooseEqualizer(name: String) {
        appPrefs?.setEqPreset(name)
        applyEqualizer()
    }

    // Remembers where a local file is, about every five seconds, so it continues there next time
    private fun saveResumePoint(uri: Uri, positionMs: Long, durationMs: Long) {
        val saved = appPrefs ?: return
        if (!saved.resumeEnabled || saved.privateMode) return
        val scheme = uri.scheme ?: return
        if (scheme != "file" && scheme != "content") return
        val now = SystemClock.elapsedRealtime()
        if (now - lastResumeSave < 5000L) return
        lastResumeSave = now
        if (durationMs > 0L && positionMs > durationMs - 10_000L) saved.clearResume(uri)
        else if (positionMs > 10_000L) saved.saveResume(uri, positionMs)
    }

    fun setSpeed(value: Float) {
        playbackSpeed = value
        engine?.setPlaybackSpeed(value)
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        if (queue.isEmpty()) return
        rebuildOrder(order.getOrNull(orderPos) ?: 0)
        NowPlayingBridge.setAvailability(hasNext(), hasPrevious())
    }

    // Whether the notification's next and previous buttons have something to go to
    fun hasNext(): Boolean = orderPos + 1 < order.size || repeat == RepeatMode.ALL

    fun hasPrevious(): Boolean = orderPos > 0

    // Gives the notification, lock screen and headset controls what is playing, and the next and
    // previous actions. The album art is the track's thumbnail, sent as a JPEG.
    private fun publishNowPlaying(item: MediaItem, context: Context) {
        NowPlayingBridge.onNext = { next() }
        NowPlayingBridge.onPrevious = { previous() }
        NowPlayingBridge.update(
            title = item.title,
            artist = item.artist,
            album = item.album,
            hasNext = hasNext(),
            hasPrevious = hasPrevious()
        )
        val uri = item.uri
        artworkScope.launch {
            val bitmap = withContext(Dispatchers.IO) { loadThumbnail(context, uri) }
            val bytes = bitmap?.let { artworkJpeg(it) }
            if (currentUri == uri) NowPlayingBridge.setArtwork(bytes)
        }
    }

    private fun artworkJpeg(bitmap: Bitmap): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        return out.toByteArray()
    }

    fun cycleRepeat() {
        repeat = when (repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        NowPlayingBridge.setAvailability(hasNext(), hasPrevious())
    }

    fun applyRepeat(mode: RepeatMode) {
        repeat = mode
        NowPlayingBridge.setAvailability(hasNext(), hasPrevious())
    }

    // The queue in play order, and where the current track sits in it (for the Playlist sheet)
    fun queueEntries(): List<MediaItem> = order.mapNotNull { queue.getOrNull(it) }

    val queuePosition: Int get() = orderPos

    // Jumps to an entry of the queue as listed by [queueEntries]
    fun playAt(position: Int) {
        if (position !in order.indices) return
        orderPos = position
        loadCurrent()
    }

    // Next track. At the end of the queue it wraps only when Repeat all is on.
    fun next(): Boolean = advance(wrap = repeat == RepeatMode.ALL)

    // Previous track, or back to the start of the current track if it has played for a few seconds
    fun previous() {
        val position = engine?.snapshot?.value?.positionMs ?: 0L
        if (position > PREVIOUS_RESTART_MS) {
            seekTo(0L)
        } else if (orderPos > 0) {
            orderPos--
            loadCurrent()
        } else {
            seekTo(0L)
        }
    }

    // Stops playback and removes the queue
    fun stop() {
        cancelSleep()
        clearAb()
        releaseEngine()
        title = ""
        currentUri = null
        status = ""
        queue = emptyList()
        order = emptyList()
        orderPos = 0
        NowPlayingBridge.clear()
    }

    private fun rebuildOrder(startIndex: Int) {
        val n = queue.size
        if (shuffle) {
            order = listOf(startIndex) + (0 until n).filter { it != startIndex }.shuffled()
            orderPos = 0
        } else {
            order = (0 until n).toList()
            orderPos = startIndex
        }
    }

    private fun currentItem(): MediaItem? {
        val index = order.getOrNull(orderPos) ?: return null
        return queue.getOrNull(index)
    }

    private fun advance(wrap: Boolean): Boolean {
        when {
            orderPos + 1 < order.size -> orderPos++
            wrap && order.isNotEmpty() -> {
                orderPos = 0
                if (shuffle) order = (0 until queue.size).shuffled()
            }
            else -> {
                status = "Finished"
                return false
            }
        }
        return loadCurrent()
    }

    private fun onEnded() {
        if (endHandled) return
        endHandled = true
        currentUri?.let { appPrefs?.clearResume(it) }
        if (sleepAfterTrack) {
            // Sleep timer set to "end of track": stay stopped
            sleepAfterTrack = false
            return
        }
        if (repeat == RepeatMode.ONE) {
            seekTo(0L)
            engine?.play()
        } else {
            advance(wrap = repeat == RepeatMode.ALL)
        }
    }

    private fun loadCurrent(): Boolean {
        val context = appContext ?: return false
        val item = currentItem() ?: return false
        if ((item.uri.scheme?.lowercase() ?: "") !in SUPPORTED) {
            status = "This kind of address cannot be played here."
            return false
        }
        if (engine == null) createEngine(context)
        val e = engine ?: return false
        title = item.title
        currentUri = item.uri
        kind = if (item.isVideo) "video" else "audio"
        status = "Opening..."
        recent = (listOf(item) + recent.filter { it.uri != item.uri }).take(RECENT_MAX)
        clearAb()
        equalizerApplied = false
        lastResumeSave = SystemClock.elapsedRealtime()
        val saved = appPrefs
        val scheme = item.uri.scheme ?: ""
        val resumeAt = if (saved != null && saved.resumeEnabled && !saved.privateMode && (scheme == "file" || scheme == "content")) {
            saved.resumePosition(item.uri)
        } else {
            0L
        }
        e.open(PlaybackSource.fromUri(item.uri, title = item.title, startPositionMs = resumeAt))
        publishNowPlaying(item, context)
        if (connector == null) {
            connector = PlaybackServiceConnector(context).also { it.connect() }
        }
        return true
    }

    private fun createEngine(context: Context) {
        val e = VoidEngines.createDefault(context)
        engine = e
        playbackSpeed = 1f
        val s = MainScope()
        scope = s
        s.launch {
            e.snapshot.collect { snap ->
                playing = snap.playWhenReady
                status = describe(snap.state, snap.error?.toString())
                if (snap.state == PlaybackState.ENDED) onEnded() else endHandled = false
                if (!equalizerApplied && snap.equalizerBandsHz.isNotEmpty()) applyEqualizer()
                // A-B repeat: jump back to the start mark when the end mark is reached
                if (snap.state == PlaybackState.PLAYING && abStartMs >= 0L && abEndMs > abStartMs && snap.positionMs >= abEndMs) {
                    e.seekTo(abStartMs)
                }
                val now = currentUri
                if (now != null && snap.source?.uri == now.toString() &&
                    (snap.state == PlaybackState.PLAYING || snap.state == PlaybackState.PAUSED)
                ) {
                    saveResumePoint(now, snap.positionMs, snap.durationMs)
                }
            }
        }
    }

    private fun releaseEngine() {
        // Stop listening first, so the old engine's last updates cannot overwrite the app's state
        scope?.cancel()
        scope = null
        engine?.release()
        engine = null
        playing = false
        endHandled = false
        playbackSpeed = 1f
    }

    private fun describe(state: PlaybackState, error: String?): String = when (state) {
        PlaybackState.IDLE -> ""
        PlaybackState.PREPARING -> "Opening..."
        PlaybackState.READY -> "Ready"
        PlaybackState.PLAYING -> "Playing"
        PlaybackState.PAUSED -> "Paused"
        PlaybackState.BUFFERING -> "Buffering..."
        PlaybackState.ENDED -> "Finished"
        PlaybackState.ERROR -> "Error: ${error ?: "this file could not be played"}"
        PlaybackState.RELEASED -> ""
    }
}
