package com.hmedia.player.search

import org.json.JSONException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchParseTest {
    @Test
    fun readsResults() {
        val body = """{"query":"dragon","results":[{"title":"A","url":"https://example.com/a","snippet":"s"}]}"""
        val results = SearchApi.parseResults(body)
        assertEquals(1, results.size)
        assertEquals("https://example.com/a", results[0].url)
    }

    @Test
    fun dropsLinksThatAreNotWebAddresses() {
        val body = """{"results":[{"title":"bad","url":"javascript:alert(1)"},{"title":"ok","url":"http://example.org"}]}"""
        assertEquals(listOf("http://example.org"), SearchApi.parseResults(body).map { it.url })
    }

    @Test
    fun emptyResultsAreEmptyNotAnError() {
        assertTrue(SearchApi.parseResults("""{"results":[]}""").isEmpty())
    }

    @Test(expected = JSONException::class)
    fun malformedBodyFails() {
        SearchApi.parseResults("not json")
    }
}
