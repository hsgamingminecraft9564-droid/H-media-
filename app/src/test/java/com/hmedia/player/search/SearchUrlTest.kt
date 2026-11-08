package com.hmedia.player.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchUrlTest {
    private fun invalidReason(raw: String?): String {
        val check = SearchUrl.checkBase(raw)
        assertTrue("expected invalid for $raw", check is BaseUrlCheck.Invalid)
        return (check as BaseUrlCheck.Invalid).reason
    }

    @Test
    fun httpsAddressIsAcceptedWithTrailingSlashRemoved() {
        val check = SearchUrl.checkBase("https://search.my-host.net/")
        assertEquals(BaseUrlCheck.Ok("https://search.my-host.net"), check)
    }

    @Test
    fun addressesThatCannotWorkAreRejectedWithAReason() {
        assertEquals("The search server address is empty.", invalidReason(""))
        assertEquals("The search server address is empty.", invalidReason(null))
        assertEquals("The search server address must start with https://", invalidReason("http://s.example.net"))
        assertEquals("The search server address contains spaces.", invalidReason("https://s .example.net"))
        assertEquals("The search server address has no host name.", invalidReason("https:///path"))
        assertEquals("The search server address must not contain a query or fragment.",
            invalidReason("https://s.example.net?x=1"))
        assertEquals("The search server address must not contain a username or password.",
            invalidReason("https://me:secret@s.example.net"))
        assertEquals("The search server address is too long.", invalidReason("https://" + "a".repeat(400) + ".net"))
    }

    @Test
    fun placeholderAddressIsReportedAsNotSetUp() {
        assertEquals("Search server not set up yet.", invalidReason("https://search.example.com"))
    }

    @Test
    fun queryIsEncodedSoItCannotChangeTheRequest() {
        val url = SearchUrl.buildSearchUrl("https://s.example.net", "  a&b=c/../x  ")
        assertEquals("https://s.example.net/search?q=a%26b%3Dc%2F..%2Fx", url)
    }
}
