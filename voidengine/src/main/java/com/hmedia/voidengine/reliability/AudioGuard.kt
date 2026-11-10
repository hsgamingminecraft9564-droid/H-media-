package com.hmedia.voidengine.reliability

/**
 * Everything that can ask playback to pause for reasons outside the user's control:
 * audio focus (calls, other apps) and headphones being unplugged.
 */
interface AudioGuard {
    interface Callback {
        /** transient = true: may resume later via [onResumeAllowed]. false: stay paused. */
        fun onPauseRequested(transient: Boolean)
        fun onResumeAllowed()
        fun onDuck(duck: Boolean)
    }

    var callback: Callback?

    /** Request audio focus (+ start watching for "becoming noisy"). Returns false if denied. */
    fun acquire(): Boolean

    /** Abandon focus and stop watching. Safe to call repeatedly. */
    fun release()
}

/** Grants everything, does nothing. Used in tests or when you manage focus yourself. */
class NoAudioGuard : AudioGuard {
    override var callback: AudioGuard.Callback? = null
    override fun acquire(): Boolean = true
    override fun release() = Unit
}
