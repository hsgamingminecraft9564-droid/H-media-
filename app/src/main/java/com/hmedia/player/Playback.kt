package com.hmedia.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hmedia.voidengine.PlaybackSource
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

    private var queue: List<MediaItem> = emptyList()
    private var order: List<Int> = emptyList()
    private var orderPos = 0

    // True from the moment an item is seen finished until the engine reports any other state, so a
    // single finish is never handled twice (which would skip a track).
    private var endHandled = false
    private var scope: CoroutineScope? = null
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

    fun setSpeed(value: Float) {
        playbackSpeed = value
        engine?.setPlaybackSpeed(value)
    }

    fun toggleShuffle() {
        shuffle = !shuffle
        if (queue.isEmpty()) return
        rebuildOrder(order.getOrNull(orderPos) ?: 0)
    }

    fun cycleRepeat() {
        repeat = when (repeat) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
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
        releaseEngine()
        title = ""
        currentUri = null
        status = ""
        queue = emptyList()
        order = emptyList()
        orderPos = 0
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
        e.open(PlaybackSource.fromUri(item.uri, title = item.title))
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
