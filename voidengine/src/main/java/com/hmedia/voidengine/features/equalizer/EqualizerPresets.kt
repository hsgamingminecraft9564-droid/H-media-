package com.hmedia.voidengine.features.equalizer

import com.hmedia.voidengine.EqualizerSettings
import kotlin.math.exp

/** Presets are shapes computed for any band count, because VLC and Android expose different band counts. */
object EqualizerPresets {
    const val FLAT = "Flat"
    const val BASS_BOOST = "Bass Boost"
    const val TREBLE_BOOST = "Treble Boost"
    const val VOCAL = "Vocal"

    val names: List<String> = listOf(FLAT, BASS_BOOST, TREBLE_BOOST, VOCAL)

    fun forName(name: String, bandCount: Int): EqualizerSettings {
        val count = bandCount.coerceAtLeast(0)
        val gains = List(count) { index ->
            // 0.0 = lowest band, 1.0 = highest band
            val x = if (count <= 1) 0f else index.toFloat() / (count - 1).toFloat()
            when (name) {
                BASS_BOOST -> 6f * (1f - x) * (1f - x)
                TREBLE_BOOST -> 6f * x * x
                VOCAL -> 4f * exp(-((x - 0.6f) * (x - 0.6f)) / 0.03f)
                else -> 0f
            }
        }
        return EqualizerSettings(enabled = name != FLAT, preAmpDb = 0f, bandGainsDb = gains)
    }
}
