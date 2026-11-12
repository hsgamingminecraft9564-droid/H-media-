package com.hmedia.player.search

import java.net.URLEncoder

// Result of checking the search server address
sealed class BaseUrlCheck {
    data class Ok(val base: String) : BaseUrlCheck()
    data class Invalid(val reason: String) : BaseUrlCheck()
}

// Checks the search server address before the app uses it, and builds request addresses.
// Pure Java, so it can be unit tested on a computer. The messages are the ones the tests expect.
object SearchUrl {
    private const val PLACEHOLDER = "https://search.example.com"
    private const val MAX_LENGTH = 300
    private val HOST_AND_PORT = Regex(
        "^[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?(\\.[A-Za-z0-9]([A-Za-z0-9-]*[A-Za-z0-9])?)*(:[0-9]{1,5})?$"
    )
    private val SCHEME_AND_REST = Regex("^([A-Za-z][A-Za-z0-9+.-]*)://(.*)$", RegexOption.DOT_MATCHES_ALL)

    // Checks the address in a fixed order: the first problem found is the one reported
    fun checkBase(raw: String?): BaseUrlCheck {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return BaseUrlCheck.Invalid("The search server address is empty.")
        if (text.length > MAX_LENGTH) return BaseUrlCheck.Invalid("The search server address is too long.")
        if (text.any { it.isWhitespace() }) return BaseUrlCheck.Invalid("The search server address contains spaces.")

        val parts = SCHEME_AND_REST.matchEntire(text)
        if (parts == null || parts.groupValues[1].lowercase() != "https") {
            return BaseUrlCheck.Invalid("The search server address must start with https://")
        }
        val rest = parts.groupValues[2]

        // The host part ends at the first slash, question mark or hash
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
            .let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, authorityEnd)
        val tail = rest.substring(authorityEnd)

        if (authority.contains('@')) {
            return BaseUrlCheck.Invalid("The search server address must not contain a username or password.")
        }
        if (authority.isEmpty()) return BaseUrlCheck.Invalid("The search server address has no host name.")
        if (!HOST_AND_PORT.matches(authority)) {
            return BaseUrlCheck.Invalid("The search server host name is not valid.")
        }
        if (tail.contains('?') || tail.contains('#')) {
            return BaseUrlCheck.Invalid("The search server address must not contain a query or fragment.")
        }
        if (tail.isNotEmpty() && tail != "/") {
            return BaseUrlCheck.Invalid("The search server address must be the server root, without a path.")
        }

        val base = "https://$authority"
        if (base == PLACEHOLDER) return BaseUrlCheck.Invalid("Search server not set up yet.")
        return BaseUrlCheck.Ok(base)
    }

    // The request address for one search: the server root, then /search?q=... with the query encoded
    fun buildSearchUrl(base: String, query: String): String {
        val root = base.trim().trimEnd('/')
        return "$root/search?q=${URLEncoder.encode(query.trim(), "UTF-8")}"
    }
}
