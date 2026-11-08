package com.hmedia.player.engine

// The decisions behind automatic reconnect, with no Android types so they can be tested on a computer.
object ReconnectPolicy {
    const val MAX_RETRIES = 30
    const val BASE_DELAY_MS = 2_000L
    const val MAX_DELAY_MS = 60_000L

    // A retry only counts once playback has lasted this long
    const val STABLE_MS = 10_000L

    enum class Decision { RECONNECT, GIVE_UP, NONE }

    // Delay before retry number `attempt` (counting from 1). It grows each time, up to one minute.
    fun delayMs(attempt: Int): Long = (BASE_DELAY_MS * attempt).coerceAtMost(MAX_DELAY_MS)

    // Only a network stream reconnects. A released engine, a user pause, a finished file, or a local file never does.
    fun decide(
        isNetwork: Boolean,
        released: Boolean,
        userPaused: Boolean,
        nearEnd: Boolean,
        attemptsSoFar: Int
    ): Decision = when {
        released || userPaused -> Decision.NONE
        !isNetwork -> Decision.NONE
        nearEnd -> Decision.NONE
        attemptsSoFar >= MAX_RETRIES -> Decision.GIVE_UP
        else -> Decision.RECONNECT
    }

    // True when playback has lasted long enough that earlier failures no longer count
    fun clearRetriesNow(retries: Int, playingForMs: Long): Boolean =
        retries > 0 && playingForMs >= STABLE_MS
}
