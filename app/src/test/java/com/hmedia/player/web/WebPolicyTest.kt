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
        assertFalse(WebPolicy.isAllowedPage("https://example.com/" + "a".repeat(3000)))
    }
}
