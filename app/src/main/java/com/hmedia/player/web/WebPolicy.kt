package com.hmedia.player.web

import java.net.URI

// Which pages the search result viewer may open. Pure rules, so they can be tested on a computer.
object WebPolicy {
    private const val MAX_LENGTH = 2048

    // A page may open only if it is a plain web address: http or https, with a host name, no spaces,
    // no username or password (those can hide where a link really goes), and not absurdly long.
    fun isAllowedPage(raw: String?): Boolean {
        if (raw.isNullOrEmpty() || raw.length > MAX_LENGTH) return false
        if (raw.any { it.isWhitespace() }) return false
        val uri = try {
            URI(raw)
        } catch (e: Exception) {
            return false
        }
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme != "http" && scheme != "https") return false
        if (uri.host.isNullOrEmpty()) return false
        if (uri.rawUserInfo != null) return false
        return true
    }
}
