package com.hmedia.voidengine

import org.junit.Assert.assertEquals
import org.junit.Test

class DefaultBackendSelectorTest {

    private fun provider(
        name: String,
        types: Set<SourceType> = SourceType.values().toSet(),
        features: Set<Feature> = emptySet(),
        schemes: Set<String>? = null,
        extensions: Set<String> = emptySet()
    ) = BackendProvider(BackendCapabilities(name, types, features, schemes, extensions)) { FakeBackend() }

    private fun names(source: PlaybackSource, vararg providers: BackendProvider) =
        DefaultBackendSelector().candidates(source, providers.toList()).map { it.capabilities.name }

    @Test
    fun keepsListOrderWithoutHints() {
        val source = PlaybackSource("https://x/y.bin", SourceType.HTTP)
        assertEquals(listOf("m", "v"), names(source, provider("m"), provider("v")))
    }

    @Test
    fun extensionPreferenceMovesBackendFirst() {
        val source = PlaybackSource("file:///a.avi", SourceType.LOCAL_FILE)
        assertEquals(listOf("v", "m"), names(source, provider("m"), provider("v", extensions = setOf("avi"))))
    }

    @Test
    fun preferredBackendWinsOverExtension() {
        val source = PlaybackSource("file:///a.avi", SourceType.LOCAL_FILE, preferredBackend = "m")
        assertEquals(listOf("m", "v"), names(source, provider("m"), provider("v", extensions = setOf("avi"))))
    }

    @Test
    fun filtersBySchemeAndKeepsAnySchemeWhenNull() {
        val source = PlaybackSource("vault://abc", SourceType.LOCAL_FILE)
        assertEquals(
            listOf("m"),
            names(source, provider("m", schemes = setOf("file", "vault")), provider("v", schemes = setOf("file")))
        )
        assertEquals(listOf("m", "v"), names(source, provider("m"), provider("v")))
    }

    @Test
    fun filtersByRequiredFeatures() {
        val source = PlaybackSource("file:///a.mp3", SourceType.LOCAL_FILE, requiredFeatures = setOf(Feature.EQUALIZER))
        assertEquals(
            listOf("v"),
            names(source, provider("m"), provider("v", features = setOf(Feature.EQUALIZER)))
        )
    }

    @Test
    fun filtersBySourceType() {
        val source = PlaybackSource("rtsp://h/x", SourceType.RTSP)
        assertEquals(
            listOf("v"),
            names(source, provider("m", types = setOf(SourceType.HTTP)), provider("v", types = setOf(SourceType.RTSP)))
        )
    }

    @Test
    fun untypedSourceIsClassifiedFromItsUri() {
        val source = PlaybackSource("rtmp://h/live", SourceType.UNKNOWN)
        assertEquals(listOf("v"), names(source, provider("m", types = setOf(SourceType.HTTP)), provider("v", types = setOf(SourceType.RTMP))))
    }

    @Test
    fun unrecognisedSchemeIsRejected() {
        assertEquals(emptyList<String>(), names(PlaybackSource("gopher://h/x"), provider("m")))
        assertEquals(emptyList<String>(), names(PlaybackSource("movie.mp4"), provider("m")))
    }
}
