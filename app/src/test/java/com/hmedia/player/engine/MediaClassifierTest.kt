package com.hmedia.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaClassifierTest {
    @Test
    fun everyNetworkSchemeIsSupportedAndIsAStream() {
        for (scheme in listOf("http", "https", "rtsp", "rtmp", "udp", "mms")) {
            assertTrue(scheme, MediaClassifier.isNetwork(scheme))
            assertTrue(scheme, MediaClassifier.isSupported(scheme))
            assertEquals(scheme, MediaKind.STREAM, MediaClassifier.kind(scheme, "/clip.mp3", "clip.mp3"))
        }
    }

    @Test
    fun unsupportedAndMalformedAddressesAreRefused() {
        for (scheme in listOf<String?>(null, "", "ftp", "javascript", "intent", "market")) {
            assertFalse(scheme.toString(), MediaClassifier.isSupported(scheme))
        }
    }

    @Test
    fun localAudioAndVideoAreClassifiedByPathAndExtension() {
        assertEquals(MediaKind.AUDIO, MediaClassifier.kind("content", "/external/audio/media/12", "12"))
        assertEquals(MediaKind.VIDEO, MediaClassifier.kind("content", "/external/video/media/3", "3"))
        assertEquals(MediaKind.AUDIO, MediaClassifier.kind("file", "/sdcard/song.MP3", "song.MP3"))
        assertEquals(MediaKind.VIDEO, MediaClassifier.kind("file", "/sdcard/movie.mkv", "movie.mkv"))
    }

    @Test
    fun localSchemesAreSupportedButNotNetwork() {
        assertTrue(MediaClassifier.isSupported("content"))
        assertFalse(MediaClassifier.isNetwork("content"))
    }
}
