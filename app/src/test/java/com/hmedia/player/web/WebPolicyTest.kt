package com.hmedia.player.web

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WebPolicyTest {
    @Test
    fun webAddressesMayOpen() {
        assertTrue(WebPolicy.isAllowedPage("https://example.com/page?x=1"))
        assertTrue(WebPolicy.isAllowedPage("http://example.org"))
        assertTrue(WebPolicy.isAllowedPage("HTTPS://Example.com"))
    }

    @Test
    fun linksWithSpecialCharactersOpen() {
        assertTrue(WebPolicy.isAllowedPage("https://example.com/search?q=a|b[1]^{x}&lang=ne"))
        assertTrue(WebPolicy.isAllowedPage("https://example.com/path with spaces/page"))
        assertTrue(WebPolicy.isAllowedPage("https://example.com/\u0928\u0947\u092a\u093e\u0932\u0940?q=\u090a"))
        assertTrue(WebPolicy.isAllowedPage("https://example.com/100%"))
        assertTrue(WebPolicy.isAllowedPage("https://example.com/%E0%A4%A8"))
    }

    @Test
    fun longButReasonableLinksOpen() {
        assertTrue(WebPolicy.isAllowedPage("https://example.com/" + "a".repeat(5000)))
    }

    @Test
    fun appAndScriptSchemesAreRefused() {
        for (url in listOf("javascript:alert(1)", "intent://example#Intent;end", "market://details?id=x",
            "file:///sdcard/secret.txt", "content://media/external/video/1", "data:text/html,hi")) {
            assertFalse(url, WebPolicy.isAllowedPage(url))
        }
    }

    @Test
    fun linksThatHideTheirDestinationAreRefused() {
        assertFalse(WebPolicy.isAllowedPage("https://bank.example@evil.example/login"))
        assertFalse(WebPolicy.isAllowedPage("https://user:pass@example.com"))
    }

    @Test
    fun malformedOrOversizedAddressesAreRefused() {
        assertFalse(WebPolicy.isAllowedPage(null))
        assertFalse(WebPolicy.isAllowedPage(""))
        assertFalse(WebPolicy.isAllowedPage("https://"))
        assertFalse(WebPolicy.isAllowedPage("https://exa mple.com"))
        assertFalse(WebPolicy.isAllowedPage("https://example.com/" + "a".repeat(9000)))
    }
}
