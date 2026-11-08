package com.hmedia.player.search

import java.net.URI
import java.net.URLEncoder

// The result of checking the search server address from the build settings
sealed class BaseUrlCheck {
    data class Ok(val base: String) : BaseUrlCheck()
    data class Invalid(val reason: String) : BaseUrlCheck()
}

// Rules for the search server address and the search request. No Android types, so they can be tested on a computer.
object SearchUrl {
    // The placeholder the project ships with until a real server is set
    const val PLACEHOLDER_HOST = "search.example.com"
    private const val MAX_LENGTH = 300

    // Only an https address with a host name is accepted. Usernames, passwords, queries and fragments are refused,
    // because they would be sent with every search and could hide the real destination.
    fun checkBase(raw: String?): BaseUrlCheck {
        val value = raw?.trim().orEmpty()
        if (value.isEmpty()) return BaseUrlCheck.Invalid("The search server address is empty.")
        if (value.length > MAX_LENGTH) return BaseUrlCheck.Invalid("The search server address is too long.")
        if (value.any { it.isWhitespace() }) return BaseUrlCheck.Invalid("The search server address contains spaces.")
        val uri = try {
            URI(value)
        } catch (e: Exception) {
            return BaseUrlCheck.Invalid("The search server address is not a valid web address.")
        }
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return BaseUrlCheck.Invalid("The search server address must start with https://")
        }
        val host = uri.host
        if (host.isNullOrEmpty()) return BaseUrlCheck.Invalid("The search server address has no host name.")
        if (uri.rawUserInfo != null) {
            return BaseUrlCheck.Invalid("The search server address must not contain a username or password.")
        }
        if (uri.rawQuery != null || uri.rawFragment != null) {
            return BaseUrlCheck.Invalid("The search server address must not contain a query or fragment.")
        }
        if (host.equals(PLACEHOLDER_HOST, ignoreCase = true)) {
            return BaseUrlCheck.Invalid("Search server not set up yet.")
        }
        return BaseUrlCheck.Ok(value.trimEnd('/'))
    }

    // The query is encoded, so it cannot add parameters or change the path of the request
    fun buildSearchUrl(base: String, query: String): String =
        "$base/search?q=${URLEncoder.encode(query.trim(), "UTF-8")}"
}
