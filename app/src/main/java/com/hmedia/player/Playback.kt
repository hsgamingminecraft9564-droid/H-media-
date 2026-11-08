package com.hmedia.player

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.hmedia.player.engine.MediaClassifier
import com.hmedia.player.engine.VoidEngine
import com.hmedia.player.engine.VoidPresets
import com.hmedia.player.engine.currentNetwork
import com.hmedia.player.engine.isLowRam

// Video, audio, or stream, decided by MediaClassifier. The learner keeps separate records for each.
fun mediaKind(uri: Uri): String = MediaClassifier.kind(uri.scheme, uri.path, uri.lastPathSegment).name.lowercase()

// Owns the one file that is playing. It lives as long as the app process, not as long as a screen,
// so music keeps playing when the screen is off or another app is open. PlaybackService keeps the
// process alive and shows the notification and lock-screen controls.
object Playback {
    var engine by mutableStateOf<VoidEngine?>(null)
        private set
    var title by mutableStateOf("")
        private set
    var presetName by mutableStateOf("")
        private set
    var playing by mutableStateOf(false)
        private set
    var status by mutableStateOf("")
        private set
    private var loadedUri = ""

    val hasEngine: Boolean get() = engine != null

    fun isPlaying(): Boolean = engine?.isPlayingNow() ?: false

    // Starts a file. Does nothing if that same file is already loaded (for example, reopening the player)
    fun play(context: Context, prefs: AppPrefs, target: Uri, name: String): Boolean {
        // Unsupported or malformed addresses are refused before anything is stopped
        if (!MediaClassifier.isSupported(target.scheme)) {
            status = "This kind of address cannot be played here."
            return false
        }
        if (engine != null && loadedUri == target.toString()) return true
        releaseEngine()
        val app = context.applicationContext
        val kind = mediaKind(target)
        val net = currentNetwork(app)
        val learnKey = "${net.name}|$kind"
        val preset = VoidPresets.byId(
            if (prefs.smartEngine) prefs.learner.choose(learnKey, kind, net, isLowRam(app)) else prefs.engineId
        )
        status = ""
        val e = VoidEngine(
            app, preset, learnKey, kind,
            // Private Mode: playback is not used for learning
            onSession = { s -> if (prefs.smartEngine && !prefs.privateMode) prefs.learner.record(s) },
            onStatus = { msg -> status = msg }
        )
        e.onPlayingChanged = { now ->
            playing = now
            PlaybackService.refresh()
        }
        engine = e
        loadedUri = target.toString()
        title = name
        presetName = preset.name
        PlaybackService.start(app)
        e.open(target)
        PlaybackService.refresh()
        return true
    }

    fun toggle() {
        engine?.toggle()
    }

    fun pause() {
        engine?.pause()
    }

    fun resume() {
        engine?.let { if (!it.isPlayingNow()) it.toggle() }
    }

    fun seekTo(ms: Long) {
        engine?.seekTo(ms)
    }

    // Stops playback and removes the notification
    fun stop() {
        releaseEngine()
        title = ""
        presetName = ""
        PlaybackService.instance?.shutdown()
    }

    private fun releaseEngine() {
        engine?.release()
        engine = null
        loadedUri = ""
        playing = false
    }
}
