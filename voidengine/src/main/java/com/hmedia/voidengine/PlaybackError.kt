package com.hmedia.voidengine

sealed class PlaybackError(
    open val message: String,
    open val cause: Throwable? = null,
    /** True if the UI may offer "retry". */
    val recoverable: Boolean = false
) {
    data class SourceNotFound(override val message: String = "Source not found", override val cause: Throwable? = null) : PlaybackError(message, cause)
    data class Network(override val message: String = "Network error", override val cause: Throwable? = null) : PlaybackError(message, cause, recoverable = true)
    /** The URL/path is malformed or not allowed (bad scheme, no host, embedded credentials...). Never retried. */
    data class InvalidSource(override val message: String = "Invalid source", override val cause: Throwable? = null) : PlaybackError(message, cause)
    /** HTTP 401/403 or equivalent. Never retried or sent to another backend. */
    data class Authentication(override val message: String = "Authentication required", override val cause: Throwable? = null) : PlaybackError(message, cause)
    /** Other HTTP failures. 5xx and 408/429 are recoverable; other 4xx are not. */
    data class HttpStatus(val code: Int, override val message: String = "HTTP error $code", override val cause: Throwable? = null) :
        PlaybackError(message, cause, recoverable = code >= 500 || code == 408 || code == 429)
    data class UnsupportedFormat(override val message: String = "Unsupported format", override val cause: Throwable? = null) : PlaybackError(message, cause)
    data class Decoder(override val message: String = "Decoder error", override val cause: Throwable? = null) : PlaybackError(message, cause)
    data class Backend(override val message: String = "Backend error", override val cause: Throwable? = null) : PlaybackError(message, cause, recoverable = true)
    data class Unknown(override val message: String = "Unknown error", override val cause: Throwable? = null) : PlaybackError(message, cause)
}
