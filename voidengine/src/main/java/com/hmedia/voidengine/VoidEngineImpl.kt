package com.hmedia.voidengine

import android.view.SurfaceView
import com.hmedia.voidengine.features.url.SourceClassifier
import com.hmedia.voidengine.reliability.AudioGuard
import com.hmedia.voidengine.reliability.NoAudioGuard
import com.hmedia.voidengine.reliability.ReconnectPolicy
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Single-threaded owner of all playback state.
 *
 * Reliability rules (Phase 4):
 *  - Every command and every backend callback is queued onto ONE dispatcher (default: main thread,
 *    non-immediate), so operations are strictly serialized and never re-entrant.
 *  - A generation counter drops late callbacks from an old source/session.
 *  - The engine never restarts by itself after pause()/stop()/focus loss. Reconnect only happens for
 *    remote streams that dropped while the user wanted playback, with ONE cancellable job, bounded
 *    attempts, and it is cancelled by pause/stop/setSource/release.
 *  - Audio focus and "becoming noisy" are handled once, here, through [AudioGuard].
 *  - A backend is chosen when a source is set. The only automatic switch is a fallback to the next
 *    candidate when the source fails BEFORE it ever played (explicit, bounded, visible in the snapshot).
 */
class VoidEngineImpl(
    providers: List<BackendProvider>,
    private val selector: BackendSelector = DefaultBackendSelector(),
    private val audioGuard: AudioGuard = NoAudioGuard(),
    private val reconnectPolicy: ReconnectPolicy = ReconnectPolicy(),
    dispatcher: CoroutineDispatcher = Dispatchers.Main
) : VoidEngine {

    private val providers: List<BackendProvider> = providers.toList()
    private val scope = CoroutineScope(SupervisorJob() + dispatcher)

    private val _snapshot = MutableStateFlow(PlayerSnapshot())
    override val snapshot: StateFlow<PlayerSnapshot> = _snapshot.asStateFlow()

    private val releaseRequested = AtomicBoolean(false)

    // ---- State below is touched ONLY from the engine dispatcher. ----
    private var backend: BackendAdapter? = null
    private var backendProvider: BackendProvider? = null
    private var candidates: List<BackendProvider> = emptyList()
    private var candidateIndex = 0
    private var generation = 0L
    private var videoView: SurfaceView? = null

    private var wantsPlaying = false
    private var pausedByGuard = false
    private var ducked = false
    private var everPlayed = false

    private var userVolume = 1f
    private var lastSpeed = 1f
    private var lastEqualizer: EqualizerSettings? = null

    private var reconnectJob: Job? = null
    private var reconnectAttempts = 0

    private val guardCallback = object : AudioGuard.Callback {
        override fun onPauseRequested(transient: Boolean) = post {
            if (!wantsPlaying) return@post
            wantsPlaying = false
            backend?.pause()
            update { it.copy(playWhenReady = false) }
            if (transient) {
                pausedByGuard = true
            } else {
                pausedByGuard = false
                audioGuard.release()
            }
        }

        override fun onResumeAllowed() = post {
            if (!pausedByGuard) return@post
            pausedByGuard = false
            wantsPlaying = true
            update { it.copy(playWhenReady = true) }
            backend?.play()
        }

        override fun onDuck(duck: Boolean) = post {
            ducked = duck
            backend?.setVolume(effectiveVolume())
        }
    }

    init {
        audioGuard.callback = guardCallback
    }

    // ------------------------------------------------------------------ public API

    override fun setSource(source: PlaybackSource) = post { openSource(source) }

    override fun prepare() = post {
        val b = backend ?: return@post
        if (_snapshot.value.state != PlaybackState.IDLE) return@post
        update { it.copy(state = PlaybackState.PREPARING, error = null) }
        b.prepare()
    }

    override fun play() = post {
        val b = backend ?: return@post
        when (_snapshot.value.state) {
            PlaybackState.ERROR, PlaybackState.RELEASED -> return@post
            PlaybackState.IDLE -> {
                update { it.copy(state = PlaybackState.PREPARING, error = null) }
                b.prepare()
            }
            PlaybackState.ENDED -> {
                b.seekTo(0L)
                update { it.copy(positionMs = 0L) }
            }
            else -> Unit
        }
        if (!audioGuard.acquire()) {
            wantsPlaying = false
            update { it.copy(playWhenReady = false) }
            return@post
        }
        wantsPlaying = true
        pausedByGuard = false
        cancelReconnect()
        update { it.copy(playWhenReady = true) }
        b.play()
    }

    override fun pause() = post {
        wantsPlaying = false
        pausedByGuard = false
        cancelReconnect()
        audioGuard.release()
        update { it.copy(playWhenReady = false) }
        backend?.pause()
    }

    override fun stop() = post {
        cancelReconnect()
        reconnectAttempts = 0
        everPlayed = false
        wantsPlaying = false
        pausedByGuard = false
        audioGuard.release()
        generation++
        backend?.let {
            it.stop()
            it.listener = listenerFor(generation)
        }
        update {
            it.copy(
                state = PlaybackState.IDLE,
                playWhenReady = false,
                positionMs = 0L,
                bufferedPercent = 0,
                reconnectAttempt = 0,
                audioTracks = emptyList(),
                subtitleTracks = emptyList(),
                selectedAudioId = null,
                selectedSubtitleId = null,
                error = null
            )
        }
    }

    override fun seekTo(positionMs: Long) = post {
        val b = backend ?: return@post
        val duration = _snapshot.value.durationMs
        val target = if (duration > 0) positionMs.coerceIn(0L, duration) else positionMs.coerceAtLeast(0L)
        b.seekTo(target)
        update { it.copy(positionMs = target) }
    }

    override fun setPlaybackSpeed(speed: Float) = post {
        lastSpeed = speed.coerceIn(MIN_SPEED, MAX_SPEED)
        backend?.setPlaybackSpeed(lastSpeed)
        update { it.copy(speed = lastSpeed) }
    }

    override fun setVolume(volume: Float) = post {
        userVolume = volume.coerceIn(0f, 1f)
        backend?.setVolume(effectiveVolume())
        update { it.copy(volume = userVolume) }
    }

    override fun selectAudioTrack(id: String?) = post { backend?.selectAudioTrack(id) }

    override fun selectSubtitleTrack(id: String?) = post { backend?.selectSubtitleTrack(id) }

    override fun setEqualizer(settings: EqualizerSettings?) = post {
        lastEqualizer = settings
        backend?.setEqualizer(settings)
    }

    override fun attachVideoView(view: SurfaceView) = post {
        videoView = view
        backend?.attachVideoView(view)
    }

    override fun detachVideoView() = post {
        videoView = null
        backend?.detachVideoView()
    }

    override fun release() {
        if (!releaseRequested.compareAndSet(false, true)) return
        scope.launch {
            try {
                cancelReconnect()
                audioGuard.release()
                audioGuard.callback = null
                discardBackend()
                generation++
                _snapshot.value = PlayerSnapshot(state = PlaybackState.RELEASED)
            } finally {
                scope.cancel()
            }
        }
    }

    // ------------------------------------------------------------------ internals

    private fun post(block: () -> Unit) {
        if (releaseRequested.get()) return
        scope.launch { if (!releaseRequested.get()) block() }
    }

    private fun update(block: (PlayerSnapshot) -> PlayerSnapshot) {
        _snapshot.update(block)
    }

    private fun effectiveVolume(): Float = if (ducked) userVolume * DUCK_FACTOR else userVolume

    private fun freshSnapshot(
        source: PlaybackSource?,
        backendName: String?,
        bands: List<Int>,
        error: PlaybackError?
    ): PlayerSnapshot = PlayerSnapshot(
        state = if (error != null) PlaybackState.ERROR else PlaybackState.IDLE,
        source = source,
        backendName = backendName,
        speed = lastSpeed,
        volume = userVolume,
        equalizerBandsHz = bands,
        error = error
    )

    private fun openSource(source: PlaybackSource) {
        cancelReconnect()
        reconnectAttempts = 0
        everPlayed = false
        wantsPlaying = false
        pausedByGuard = false
        audioGuard.release()
        generation++
        val invalid = SourceClassifier.validationError(source.uri)
        if (invalid != null) {
            candidates = emptyList()
            discardBackend()
            update { freshSnapshot(source, null, emptyList(), PlaybackError.InvalidSource(invalid)) }
            return
        }
        candidates = selector.candidates(source, providers)
        candidateIndex = 0
        if (candidates.isEmpty()) {
            discardBackend()
            update {
                freshSnapshot(source, null, emptyList(), PlaybackError.UnsupportedFormat("No backend can play this source"))
            }
            return
        }
        attachCandidate(source)
    }

    /** Binds candidates[candidateIndex] (or the next one that can be created) to [source]. */
    private fun attachCandidate(source: PlaybackSource): Boolean {
        while (candidateIndex < candidates.size) {
            val provider = candidates[candidateIndex]
            try {
                val b = obtainBackend(provider)
                b.listener = listenerFor(generation)
                b.setVolume(effectiveVolume())
                b.setEqualizer(lastEqualizer)
                b.setSource(source)
                b.setPlaybackSpeed(lastSpeed)
                val bands = b.equalizerBandFrequenciesHz()
                update { freshSnapshot(source, provider.capabilities.name, bands, null) }
                return true
            } catch (e: Throwable) {
                if (e is VirtualMachineError) throw e
                discardBackend()
                candidateIndex++
            }
        }
        update {
            freshSnapshot(source, null, emptyList(), PlaybackError.Backend("No backend could be started"))
        }
        return false
    }

    private fun obtainBackend(provider: BackendProvider): BackendAdapter {
        val existing = backend
        if (existing != null && backendProvider === provider) {
            existing.stop()
            return existing
        }
        discardBackend()
        val created = provider.create()
        backend = created
        backendProvider = provider
        videoView?.let { created.attachVideoView(it) }
        return created
    }

    private fun discardBackend() {
        val b = backend
        backend = null
        backendProvider = null
        if (b != null) {
            b.listener = null
            try {
                b.release()
            } catch (e: Exception) {
                // a failing release must never break the engine
            }
        }
    }

    private fun cancelReconnect() {
        reconnectJob?.cancel()
        reconnectJob = null
    }

    /** Explicit fallback: the source failed before it ever played, so try the next backend. */
    private fun tryNextCandidate(): Boolean {
        val source = _snapshot.value.source ?: return false
        if (candidateIndex + 1 >= candidates.size) return false
        val shouldPlay = wantsPlaying
        generation++
        candidateIndex++
        if (!attachCandidate(source)) return false
        val b = backend ?: return false
        update { it.copy(state = PlaybackState.PREPARING, playWhenReady = shouldPlay) }
        b.prepare()
        if (shouldPlay) b.play()
        return true
    }

    /** Returns true if a reconnect is scheduled (or already pending). Never creates a second loop. */
    private fun scheduleReconnect(): Boolean {
        if (reconnectJob?.isActive == true) return true
        if (reconnectAttempts >= reconnectPolicy.maxAttempts) return false
        val wait = reconnectPolicy.delayMs(reconnectAttempts)
        reconnectAttempts++
        val gen = generation
        update { it.copy(state = PlaybackState.BUFFERING, reconnectAttempt = reconnectAttempts) }
        reconnectJob = scope.launch {
            delay(wait)
            if (gen != generation || !wantsPlaying || releaseRequested.get()) return@launch
            reopenForReconnect()
        }
        return true
    }

    private fun reopenForReconnect() {
        val b = backend ?: return
        val current = _snapshot.value
        val source = current.source ?: return
        val resumeAt = if (current.durationMs > 0) current.positionMs else 0L
        b.stop()
        b.setSource(source.copy(startPositionMs = resumeAt))
        b.setPlaybackSpeed(lastSpeed)
        b.prepare()
        b.play()
    }

    private fun handleBackendState(state: PlaybackState) {
        when (state) {
            PlaybackState.IDLE, PlaybackState.ERROR, PlaybackState.RELEASED -> Unit
            PlaybackState.READY -> {
                val s = _snapshot.value.state
                if (s == PlaybackState.IDLE || s == PlaybackState.PREPARING || s == PlaybackState.BUFFERING) {
                    update { it.copy(state = state) }
                }
            }
            PlaybackState.PLAYING -> {
                everPlayed = true
                reconnectAttempts = 0
                update { it.copy(state = state, error = null, reconnectAttempt = 0) }
            }
            PlaybackState.ENDED -> {
                wantsPlaying = false
                pausedByGuard = false
                audioGuard.release()
                update { it.copy(state = state, playWhenReady = false) }
            }
            PlaybackState.PREPARING, PlaybackState.PAUSED, PlaybackState.BUFFERING ->
                update { it.copy(state = state) }
        }
    }

    private fun handleBackendError(error: PlaybackError) {
        val source = _snapshot.value.source
        val canFallback = !everPlayed && (
            error is PlaybackError.UnsupportedFormat ||
                error is PlaybackError.Decoder ||
                error is PlaybackError.Backend
            )
        if (canFallback && tryNextCandidate()) return
        if (error is PlaybackError.Network && wantsPlaying && source != null && source.type.isRemote && scheduleReconnect()) return
        cancelReconnect()
        wantsPlaying = false
        pausedByGuard = false
        audioGuard.release()
        update { it.copy(state = PlaybackState.ERROR, playWhenReady = false, error = error) }
    }

    private fun listenerFor(gen: Long): BackendAdapter.Listener = object : BackendAdapter.Listener {
        private fun ifCurrent(block: () -> Unit) {
            post { if (gen == generation) block() }
        }

        override fun onState(state: PlaybackState) = ifCurrent { handleBackendState(state) }

        override fun onPosition(positionMs: Long, durationMs: Long) = ifCurrent {
            update { it.copy(positionMs = positionMs, durationMs = durationMs) }
        }

        override fun onBuffering(percent: Int) = ifCurrent {
            update { it.copy(bufferedPercent = percent.coerceIn(0, 100)) }
        }

        override fun onVideoSize(width: Int, height: Int) = ifCurrent {
            update { it.copy(videoWidth = width, videoHeight = height) }
        }

        override fun onTracks(
            audio: List<TrackInfo>,
            subtitles: List<TrackInfo>,
            selectedAudioId: String?,
            selectedSubtitleId: String?
        ) = ifCurrent {
            update {
                it.copy(
                    audioTracks = audio,
                    subtitleTracks = subtitles,
                    selectedAudioId = selectedAudioId,
                    selectedSubtitleId = selectedSubtitleId
                )
            }
        }

        override fun onError(error: PlaybackError) = ifCurrent { handleBackendError(error) }
    }

    private companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
        const val DUCK_FACTOR = 0.2f
    }
}
