package com.hmedia.voidengine

import com.hmedia.voidengine.reliability.ReconnectPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReconnectPolicyTest {
    @Test
    fun delaysDoubleAndAreCapped() {
        val policy = ReconnectPolicy()
        assertEquals(1_000L, policy.delayMs(0))
        assertEquals(2_000L, policy.delayMs(1))
        assertEquals(4_000L, policy.delayMs(2))
        assertEquals(8_000L, policy.delayMs(3))
        assertEquals(15_000L, policy.delayMs(4))
        assertEquals(15_000L, policy.delayMs(500))
    }

    @Test
    fun invalidValuesAreRejected() {
        assertThrows(IllegalArgumentException::class.java) { ReconnectPolicy(maxAttempts = -1) }
        assertThrows(IllegalArgumentException::class.java) { ReconnectPolicy(baseDelayMs = 0) }
        assertThrows(IllegalArgumentException::class.java) { ReconnectPolicy(baseDelayMs = 100, maxDelayMs = 10) }
    }
}
