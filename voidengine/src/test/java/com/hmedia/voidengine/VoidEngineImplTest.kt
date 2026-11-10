package com.hmedia.voidengine

import com.hmedia.voidengine.reliability.AudioGuard
import com.hmedia.voidengine.reliability.NoAudioGuard
import com.hmedia.voidengine.reliability.ReconnectPolicy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoidEngineImplTest {

    private val local = PlaybackSource("file:///a.mp4", SourceType.LOCAL_FILE)
    private val remote = PlaybackSource("https://x.test/live.m3u8", SourceType.HLS)

    private fun provider(name: String, backend: FakeBackend) = BackendProvider(
        BackendCapabilities(name, setOf(SourceType.LOCAL_FILE, SourceType.HLS), emptySet())
    ) { backend }

    private fun TestScope.newEngine(
        vararg backends: FakeBackend,
        guard: AudioGuard = NoAudioGuard()
    ): VoidEngineImpl = VoidEngineImpl(
        providers = backends.mapIndexed { i, b -> provider("b$i", b) },
        selector = DefaultBackendSelector(),
        audioGuard = guard,
        reconnectPolicy = ReconnectPolicy(),
        dispatcher = UnconfinedTestDispatcher(testScheduler)
    )

    @Test
    fun prepareThenPlay_reachesPlaying() = runTest {
        val fake = FakeBackend()
        val engine = newEngine(fake)
        engine.setSource(local)
        engine.prepare()
        assertEquals(PlaybackState.PREPARING, engine.snapshot.value.state)
        fake.emitState(PlaybackState.READY)
        assertEquals(PlaybackState.READY, engine.snapshot.value.state)
        engine.play()
        assertEquals(1, fake.playCalls)
        assertTrue(engine.snapshot.value.playWhenReady)
        fake.emitState(PlaybackState.PLAYING)
        assertEquals(PlaybackState.PLAYING, engine.snapshot.value.state)
        engine.release()
    }

    @Test
    fun playBeforeReady_isAccepted() = runTest {
        val fake = FakeBackend()
        val engine = newEngine(fake)
        engine.setSource(local)
        engine.prepare()
        engine.play()
        assertEquals(1, fake.playCalls)
        engine.release()
    }

    @Test
    fun pause_neverTriggersReconnect() = runTest {
        val fake = FakeBackend()
        val engine = newEngine(fake)
        engine.setSource(remote)
        engine.prepare()
        engine.play()
        fake.emitState(PlaybackState.PLAYING)
        engine.pause()
        assertFalse(engine.snapshot.value.playWhenReady)
        assertEquals(1, fake.pauseCalls)
        fake.emitError(PlaybackError.Network())
        assertEquals(PlaybackState.ERROR, engine.snapshot.value.state)
        advanceTimeBy(120_000)
        assertEquals(1, fake.setSourceCalls)
        engine.release()
    }

    @Test
    fun networkDrop_whilePlaying_reconnectsWithSingleLoop() = runTest {
        val fake = FakeBackend()
        val engine = newEngine(fake)
        engine.setSource(remote)
        engine.prepare()
        engine.play()
        fake.emitState(PlaybackState.PLAYING)
        fake.emitError(PlaybackError.Network())
        fake.emitError(PlaybackError.Network())
        assertEquals(PlaybackState.BUFFERING, engine.snapshot.value.state)
        assertEquals(1, engine.snapshot.value.reconnectAttempt)
        assertEquals(1, fake.setSourceCalls)
        advanceTimeBy(1_001)
        assertEquals(2, fake.setSourceCalls)
        assertEquals(2, fake.playCalls)
        engine.release()
    }

    @Test
    fun pause_cancelsPendingReconnect() = runTest {
        val fake = FakeBackend()
        val engine = newEngine(fake)
        engine.setSource(remote)
        engine.prepare()
        engine.play()
        fake.emitState(PlaybackState.PLAYING)
        fake.emitError(PlaybackError.Network())
        engine.pause()
        advanceTimeBy(60_000)
        assertEquals(1, fake.setSourceCalls)
        engine.release()
    }

    @Test
    fun staleCallbacks_afterNewSource_areIgnored() = runTest {
        val fake = FakeBackend()
        val engine = newEngine(fake)
        engine.setSource(local)
        val old = fake.listener!!
        engine.setSource(local)
        old.onState(PlaybackState.PLAYING)
        assertEquals(PlaybackState.IDLE, engine.snapshot.value.state)
        engine.release()
    }

    @Test
    fun unsupportedFormat_beforePlaying_fallsBackToNextBackend() = runTest {
        val a = FakeBackend()
        val b = FakeBackend()
        val engine = newEngine(a, b)
        engine.setSource(local)
        engine.prepare()
        a.emitError(PlaybackError.UnsupportedFormat())
        assertEquals("b1", engine.snapshot.value.backendName)
        assertTrue(a.released)
        assertEquals(1, b.setSourceCalls)
        assertEquals(1, b.prepareCalls)
        assertEquals(PlaybackState.PREPARING, engine.snapshot.value.state)
        engine.release()
    }

    @Test
    fun errorAfterPlaying_doesNotSwitchBackend() = runTest {
        val a = FakeBackend()
        val b = FakeBackend()
        val engine = newEngine(a, b)
        engine.setSource(local)
        engine.prepare()
        engine.play()
        a.emitState(PlaybackState.PLAYING)
        a.emitError(PlaybackError.Backend())
        assertEquals(PlaybackState.ERROR, engine.snapshot.value.state)
        assertEquals(0, b.setSourceCalls)
        engine.release()
    }

    @Test
    fun noBackendForSource_reportsUnsupportedFormat() = runTest {
        val engine = VoidEngineImpl(
            providers = emptyList(),
            dispatcher = UnconfinedTestDispatcher(testScheduler)
        )
        engine.setSource(local)
        assertEquals(PlaybackState.ERROR, engine.snapshot.value.state)
        assertTrue(engine.snapshot.value.error is PlaybackError.UnsupportedFormat)
        engine.release()
    }

    @Test
    fun transientFocusLoss_pausesThenResumes() = runTest {
        val fake = FakeBackend()
        val guard = FakeGuard()
        val engine = newEngine(fake, guard = guard)
        engine.setSource(local)
        engine.prepare()
        engine.play()
        assertEquals(1, guard.acquires)
        guard.callback!!.onPauseRequested(true)
        assertEquals(1, fake.pauseCalls)
        assertFalse(engine.snapshot.value.playWhenReady)
        guard.callback!!.onResumeAllowed()
        assertEquals(2, fake.playCalls)
        assertTrue(engine.snapshot.value.playWhenReady)
        engine.release()
    }

    @Test
    fun permanentFocusLoss_doesNotResume() = runTest {
        val fake = FakeBackend()
        val guard = FakeGuard()
        val engine = newEngine(fake, guard = guard)
        engine.setSource(local)
        engine.prepare()
        engine.play()
        guard.callback!!.onPauseRequested(false)
        guard.callback!!.onResumeAllowed()
        assertEquals(1, fake.playCalls)
        assertFalse(engine.snapshot.value.playWhenReady)
        engine.release()
    }

    @Test
    fun userPause_isNotUndoneByFocusGain() = runTest {
        val fake = FakeBackend()
        val guard = FakeGuard()
        val engine = newEngine(fake, guard = guard)
        engine.setSource(local)
        engine.prepare()
        engine.play()
        engine.pause()
        guard.callback!!.onResumeAllowed()
        assertEquals(1, fake.playCalls)
        engine.release()
    }

    @Test
    fun focusDenied_doesNotStartPlayback() = runTest {
        val fake = FakeBackend()
        val guard = FakeGuard().apply { acquireResult = false }
        val engine = newEngine(fake, guard = guard)
        engine.setSource(local)
        engine.prepare()
        engine.play()
        assertEquals(0, fake.playCalls)
        assertFalse(engine.snapshot.value.playWhenReady)
        engine.release()
    }

    @Test
    fun ducking_lowersVolumeAndRestores() = runTest {
        val fake = FakeBackend()
        val guard = FakeGuard()
        val engine = newEngine(fake, guard = guard)
        engine.setSource(local)
        guard.callback!!.onDuck(true)
        assertEquals(0.2f, fake.lastVolume, 0.001f)
        guard.callback!!.onDuck(false)
        assertEquals(1f, fake.lastVolume, 0.001f)
        engine.release()
    }

    @Test
    fun stopThenPlay_restartsAndStillReceivesCallbacks() = runTest {
        val fake = FakeBackend()
        val engine = newEngine(fake)
        engine.setSource(local)
        engine.prepare()
        engine.play()
        fake.emitState(PlaybackState.PLAYING)
        engine.stop()
        assertEquals(PlaybackState.IDLE, engine.snapshot.value.state)
        assertEquals(1, fake.stopCalls)
        engine.play()
        assertEquals(2, fake.prepareCalls)
        assertEquals(2, fake.playCalls)
        fake.emitState(PlaybackState.PLAYING)
        assertEquals(PlaybackState.PLAYING, engine.snapshot.value.state)
        engine.release()
    }

    @Test
    fun playAfterEnded_seeksToStart() = runTest {
        val fake = FakeBackend()
        val engine = newEngine(fake)
        engine.setSource(local)
        engine.prepare()
        engine.play()
        fake.emitState(PlaybackState.PLAYING)
        fake.emitState(PlaybackState.ENDED)
        assertEquals(PlaybackState.ENDED, engine.snapshot.value.state)
        engine.play()
        assertEquals(0L, fake.lastSeek)
        assertEquals(2, fake.playCalls)
        engine.release()
    }

    @Test
    fun seekAndSpeed_areClamped() = runTest {
        val fake = FakeBackend()
        val engine = newEngine(fake)
        engine.setSource(local)
        fake.listener!!.onPosition(0L, 10_000L)
        engine.seekTo(50_000L)
        assertEquals(10_000L, fake.lastSeek)
        engine.seekTo(-5L)
        assertEquals(0L, fake.lastSeek)
        engine.setPlaybackSpeed(10f)
        assertEquals(4f, fake.lastSpeed, 0.001f)
        engine.setPlaybackSpeed(0.01f)
        assertEquals(0.25f, fake.lastSpeed, 0.001f)
        engine.release()
    }

    @Test
    fun release_releasesBackendAndIgnoresLaterCommands() = runTest {
        val fake = FakeBackend()
        val engine = newEngine(fake)
        engine.setSource(local)
        engine.release()
        assertEquals(PlaybackState.RELEASED, engine.snapshot.value.state)
        assertTrue(fake.released)
        engine.play()
        assertEquals(0, fake.playCalls)
    }
}
