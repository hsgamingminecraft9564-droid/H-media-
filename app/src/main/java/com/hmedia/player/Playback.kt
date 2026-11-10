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
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private val AUDIO_EXT = setOf("mp3", "flac", "m4a", "aac", "ogg", "oga", "opus", "wav", "wma")

// Only used to choose the screen layout (music or video). The VOID engine decides how to play the file.
fun mediaKind(uri: Uri): String {
    val scheme = uri.scheme ?: ""
    if (scheme.startsWith("http") || scheme == "rtsp" || scheme == "rtmp" || scheme == "udp") return "stream"
    val ext = uri.lastPathSegment?.substringAfterLast('.', "")?.lowercase() ?: ""
    return if (uri.path?.contains("/audio/") == true || ext in AUDIO_EXT) "audio" else "video"
}

// The one playing file for the whole app. It uses the VOID engine, and the engine's own background
// service keeps music playing with the screen off.
object Playback {
    private val SUPPORTED = setOf(
        "file", "content", "http", "https", "rtsp", "rtsps", "rtmp", "rtmps",
        "rtp", "udp", "mms", "mmsh", "ftp", "sftp", "smb"
    )

    var engine by mutableStateOf<VoidEngine?>(null)
        private set
    var title by mutableStateOf("")
        private set
    var kind by mutableStateOf("video")
        private set
    var playing by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set

    private var loadedUri = ""
    private var scope: CoroutineScope? = null
    private var connector: PlaybackServiceConnector? = null

    val hasEngine: Boolean get() = engine != null

    fun isPlaying(): Boolean = playing

    // Starts a file. Returns false if the address cannot be played; the reason is in [status].
    fun play(context: Context, prefs: AppPrefs, target: Uri, name: String): Boolean {
        val app = context.applicationContext
        if ((target.scheme?.lowercase() ?: "") !in SUPPORTED) {
            status = "This kind of address cannot be played here."
            return false
        }
        if (engine == null) {
            val e = VoidEngines.createDefault(app)
            engine = e
            val s = MainScope()
            scope = s
            s.launch {
                e.snapshot.collect { snap ->
                    playing = snap.playWhenReady
                    status = describe(snap.state, snap.error?.toString())
                }
            }
        }
        val e = engine ?: return false
        if (loadedUri != target.toString()) {
            loadedUri = target.toString()
            title = name
            kind = mediaKind(target)
            status = "Opening..."
            e.open(PlaybackSource.fromUri(target, title = name))
        }
        if (connector == null) {
            connector = PlaybackServiceConnector(app).also { it.connect() }
        }
        return true
    }

    fun toggle() {
        engine?.togglePlayPause()
    }

    fun pause() {
        engine?.pause()
    }

    fun resume() {
        engine?.play()
    }

    fun seekTo(ms: Long) {
        engine?.seekTo(ms)
    }

    fun stop() {
        engine?.stop()
        playing = false
        status = ""
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
