package com.hmedia.voidengine.reliability

/** Exponential back-off for dropped network streams. Delays: base, 2*base, 4*base ... capped at maxDelayMs. */
data class ReconnectPolicy(
    val maxAttempts: Int = 5,
    val baseDelayMs: Long = 1_000L,
    val maxDelayMs: Long = 15_000L
) {
    init {
        require(maxAttempts >= 0) { "maxAttempts must be >= 0" }
        require(baseDelayMs > 0) { "baseDelayMs must be > 0" }
        require(maxDelayMs >= baseDelayMs) { "maxDelayMs must be >= baseDelayMs" }
    }

    fun delayMs(attempt: Int): Long {
        val shift = attempt.coerceIn(0, 20)
        return (baseDelayMs shl shift).coerceAtMost(maxDelayMs)
    }
}
