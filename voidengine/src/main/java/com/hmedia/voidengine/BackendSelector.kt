package com.hmedia.voidengine

import com.hmedia.voidengine.features.url.SourceClassifier

interface BackendSelector {
    /** Ordered list of providers that may play [source]; the engine tries them in order (only before playback starts). */
    fun candidates(source: PlaybackSource, providers: List<BackendProvider>): List<BackendProvider>
}

/**
 * Filters by source type, URI scheme and required features, then orders by:
 * 1) source.preferredBackend, 2) providers whose preferredExtensions contain the file extension,
 * 3) the original list order.
 */
class DefaultBackendSelector : BackendSelector {
    override fun candidates(source: PlaybackSource, providers: List<BackendProvider>): List<BackendProvider> {
        val scheme = SourceClassifier.schemeOf(source.uri)
        val ext = SourceClassifier.extensionOf(source.uri)
        // A source built without a type is classified from its URI; anything still unrecognised is
        // rejected (no candidates) instead of being offered to every backend.
        val type = if (source.type == SourceType.UNKNOWN) SourceClassifier.classify(source.uri) else source.type
        if (type == SourceType.UNKNOWN || scheme.isEmpty()) return emptyList()
        val usable = providers.filter { p ->
            val c = p.capabilities
            val typeOk = type in c.supportedTypes
            val schemeOk = c.supportedSchemes == null || scheme in c.supportedSchemes
            typeOk && schemeOk && c.features.containsAll(source.requiredFeatures)
        }
        return usable.sortedBy { p ->
            val c = p.capabilities
            when {
                source.preferredBackend != null && c.name == source.preferredBackend -> 0
                ext != null && ext in c.preferredExtensions -> 1
                else -> 2
            }
        }
    }
}
