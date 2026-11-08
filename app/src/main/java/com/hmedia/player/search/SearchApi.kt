package com.hmedia.player.search

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.URLEncoder

data class SearchResult(val title: String, val url: String, val snippet: String)

// Calls our own search backend. Keys live on the server, never in the app.
// Call from a background thread.
object SearchApi {
    fun search(baseUrl: String, query: String): Result<List<SearchResult>> = try {
        Result.success(fetch(baseUrl, query))
    } catch (e: Exception) {
        Result.failure(IllegalStateException(friendly(e)))
    }

    private fun fetch(baseUrl: String, query: String): List<SearchResult> {
        val q = query.trim()
        if (q.isEmpty()) throw IllegalStateException("Type something to search for.")
        val base = when (val check = SearchUrl.checkBase(baseUrl)) {
            is BaseUrlCheck.Ok -> check.base
            is BaseUrlCheck.Invalid -> throw IllegalStateException(check.reason)
        }
        val conn = URL(SearchUrl.buildSearchUrl(base, q)).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader()?.readText().orEmpty()
            if (code !in 200..299) {
                // The server sends a plain message in JSON for expected problems such as rate limits
                val msg = runCatching { JSONObject(body).optString("error") }.getOrNull()
                throw IllegalStateException(if (msg.isNullOrEmpty()) "Search failed. Try again." else msg)
            }
            return parseResults(body)
        } finally {
            conn.disconnect()
        }
    }

    // Reads the server's JSON. Kept apart from the network code so it can be unit tested.
    // Links that are not web addresses are dropped here as well as on the server.
    fun parseResults(body: String): List<SearchResult> {
        val arr = JSONObject(body).getJSONArray("results")
        return List(arr.length()) { i ->
            val r = arr.getJSONObject(i)
            SearchResult(r.optString("title"), r.optString("url"), r.optString("snippet"))
        }.filter { it.url.startsWith("https://") || it.url.startsWith("http://") }
    }

    // Plain sentences for the user; the technical detail stays out of the screen
    private fun friendly(e: Exception): String = when (e) {
        is IllegalStateException -> e.message ?: "Search failed. Try again."
        is SocketTimeoutException -> "The search took too long. Try again."
        is IOException -> "Can't reach search right now. Check your connection."
        else -> "Search failed. Try again."
    }

    fun domainOf(url: String): String =
        runCatching { URL(url).host.removePrefix("www.") }.getOrDefault("")
}
