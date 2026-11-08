package com.hmedia.player.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetsAndRulesTest {
    @Test
    fun presetIdsAreUniqueAndTheDefaultAndFallbackExist() {
        val ids = VoidPresets.ALL.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
        assertTrue(ids.contains(VoidPresets.DEFAULT_ID))
        assertTrue(ids.contains("software")) // used by the hardware-decoding fallback
    }

    @Test
    fun everyPresetHasANameAndAnAudioOutput() {
        VoidPresets.ALL.forEach {
            assertTrue(it.id, it.name.isNotBlank())
            assertTrue(it.id, it.libOptions.contains("--aout=opensles"))
        }
    }

    @Test
    fun unknownIdFallsBackToTheDefault() {
        assertEquals(VoidPresets.DEFAULT_ID, VoidPresets.byId("no-such-preset").id)
    }

    @Test
    fun coldStartRules() {
        assertEquals("low_memory", coldStartPreset(NetKind.WIFI, "video", lowRam = true))
        assertEquals("weak_network", coldStartPreset(NetKind.CELLULAR, "stream", lowRam = false))
        assertEquals("smooth_iptv", coldStartPreset(NetKind.WIFI, "stream", lowRam = false))
        assertEquals("smooth_iptv", coldStartPreset(NetKind.CELLULAR, "video", lowRam = false))
        assertEquals(VoidPresets.DEFAULT_ID, coldStartPreset(NetKind.WIFI, "video", lowRam = false))
        assertEquals(VoidPresets.DEFAULT_ID, coldStartPreset(NetKind.WIFI, "audio", lowRam = false))
    }
}
