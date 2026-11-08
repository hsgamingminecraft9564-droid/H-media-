package com.hmedia.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconnectPolicyTest {
    private fun decide(
        isNetwork: Boolean = true, released: Boolean = false, userPaused: Boolean = false,
        nearEnd: Boolean = false, attempts: Int = 0
    ) = ReconnectPolicy.decide(isNetwork, released, userPaused, nearEnd, attempts)

    @Test
    fun delayGrowsEachAttemptAndStopsAtOneMinute() {
        assertEquals(2_000L, ReconnectPolicy.delayMs(1))
        assertEquals(4_000L, ReconnectPolicy.delayMs(2))
        assertEquals(60_000L, ReconnectPolicy.delayMs(40))
    }

    @Test
    fun aNetworkStreamReconnectsUntilTheLimit() {
        assertEquals(ReconnectPolicy.Decision.RECONNECT, decide(attempts = 0))
        assertEquals(ReconnectPolicy.Decision.RECONNECT, decide(attempts = ReconnectPolicy.MAX_RETRIES - 1))
        assertEquals(ReconnectPolicy.Decision.GIVE_UP, decide(attempts = ReconnectPolicy.MAX_RETRIES))
    }

    @Test
    fun aUserPauseNeverReconnects() {
        assertEquals(ReconnectPolicy.Decision.NONE, decide(userPaused = true))
    }

    @Test
    fun aReleasedEngineNeverReconnects() {
        assertEquals(ReconnectPolicy.Decision.NONE, decide(released = true))
    }

    @Test
    fun aFinishedStreamDoesNotReconnect() {
        assertEquals(ReconnectPolicy.Decision.NONE, decide(nearEnd = true))
    }

    @Test
    fun aLocalFileNeverReconnects() {
        assertEquals(ReconnectPolicy.Decision.NONE, decide(isNetwork = false))
    }

    @Test
    fun failuresOnlyCountAgainstAStreamThatDoesNotStayUp() {
        assertFalse(ReconnectPolicy.clearRetriesNow(retries = 3, playingForMs = 9_999))
        assertTrue(ReconnectPolicy.clearRetriesNow(retries = 3, playingForMs = 10_000))
        assertFalse(ReconnectPolicy.clearRetriesNow(retries = 0, playingForMs = 60_000))
    }
}
