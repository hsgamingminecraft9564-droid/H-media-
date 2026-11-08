package com.hmedia.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaClassifierCaseTest {
    @Test
    fun schemesAreMatchedWithoutCaseOrSpaces() {
        assertTrue(MediaClassifier.isSupported("HTTPS"))
        assertTrue(MediaClassifier.isSupported(" rtsp "))
        assertTrue(MediaClassifier.isSupported("Content"))
        assertEquals(MediaKind.STREAM, MediaClassifier.kind("HTTP", "/x.mp3", "x.mp3"))
    }

    @Test
    fun unknownOrMissingSchemesAreNotSupported() {
        assertFalse(MediaClassifier.isSupported("ftp"))
        assertFalse(MediaClassifier.isSupported("  "))
        assertFalse(MediaClassifier.isSupported(null))
        assertFalse(MediaClassifier.isNetwork(null))
    }

    @Test
    fun everyDocumentedNetworkProtocolIsOneSharedList() {
        assertEquals(setOf("http", "https", "rtsp", "rtmp", "udp", "mms"), MediaClassifier.NETWORK_SCHEMES)
    }
}
