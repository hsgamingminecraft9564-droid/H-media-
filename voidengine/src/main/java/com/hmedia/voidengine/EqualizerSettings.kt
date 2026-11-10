package com.hmedia.voidengine

/**
 * Equalizer request. [bandGainsDb] is matched to the active backend's bands in order
 * (see PlayerSnapshot.equalizerBandsHz); extra values are ignored, missing ones stay flat.
 */
data class EqualizerSettings(
    val enabled: Boolean = true,
    val preAmpDb: Float = 0f,
    val bandGainsDb: List<Float> = emptyList()
)
