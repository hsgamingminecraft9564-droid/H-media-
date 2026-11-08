package com.hmedia.player.engine

import android.content.Context
import org.json.JSONObject
import kotlin.random.Random

// One play session, measured by VoidEngine when it is released
data class SessionStats(
    val presetId: String,
    val context: String,            // network and media kind, e.g. "WIFI|video"
    val playSeconds: Double,
    val stalls: Int,
    val errors: Int,
    val startupSeconds: Double
)

// Learns on this phone which preset works best for each situation (network + media kind).
// 1. Rules pick a starting preset until there is evidence.
// 2. Learned data can replace that start only after enough sessions and a clear win (promotion gate).
// 3. Older sessions fade, so the choice adapts when a network or a preset stops working.
// Everything stays in this app's private storage. Nothing is uploaded.
class EngineLearner(context: Context) {
    private val sp = context.applicationContext.getSharedPreferences("void_learn", Context.MODE_PRIVATE)

    fun coldStart(net: NetKind, kind: String, lowRam: Boolean): String = coldStartPreset(net, kind, lowRam)

    fun choose(key: String, kind: String, net: NetKind, lowRam: Boolean): String {
        val start = coldStart(net, kind, lowRam)
        // About 1 time in 10, try a random preset so the learner keeps checking the others
        if (Random.nextDouble() < EXPLORE) return candidates(kind).random().id

        val data = load()
        val best = candidates(kind).minByOrNull { badness(data, key, it.id) } ?: return start
        if (best.id == start) return start

        val enoughSessions = plays(data, key, best.id) >= PROMOTE_MIN_PLAYS
        val clearlyBetter = badness(data, key, best.id) <= badness(data, key, start) * PROMOTE_MARGIN
        return if (enoughSessions && clearlyBetter) best.id else start
    }

    fun record(s: SessionStats) {
        if (s.playSeconds < 5.0) return // ignore very short tries
        val data = load()
        val key = "${s.context}|${s.presetId}"
        val e = data.optJSONObject(key) ?: JSONObject()
        fun decayed(name: String) = e.optDouble(name, 0.0) * DECAY
        e.put("plays", decayed("plays") + 1.0)
        e.put("sec", decayed("sec") + s.playSeconds)
        e.put("stalls", decayed("stalls") + s.stalls)
        e.put("errors", decayed("errors") + s.errors)
        e.put("startSec", decayed("startSec") + s.startupSeconds)
        data.put(key, e)
        sp.edit().putString("data", data.toString()).apply()
    }

    fun reset() {
        sp.edit().clear().apply()
    }

    // Presets that make sense for this media. Video skips the audio-only presets.
    private fun candidates(kind: String) =
        VoidPresets.ALL.filter { kind == "audio" || ":no-video" !in it.mediaOptions }

    private fun load(): JSONObject = try {
        JSONObject(sp.getString("data", "{}") ?: "{}")
    } catch (e: org.json.JSONException) {
        JSONObject() // damaged data: start fresh rather than crash
    }

    private fun plays(data: JSONObject, key: String, presetId: String): Double =
        data.optJSONObject("$key|$presetId")?.optDouble("plays", 0.0) ?: 0.0

    // Lower is better: stalls, errors and startup delay per minute of playback.
    // With few sessions, the score leans toward the neutral value.
    private fun badness(data: JSONObject, key: String, presetId: String): Double {
        val e = data.optJSONObject("$key|$presetId") ?: return UNKNOWN
        val plays = e.optDouble("plays", 0.0)
        if (plays < 0.5) return UNKNOWN
        val minutes = maxOf(e.optDouble("sec", 0.0) / 60.0, 1.0)
        val trouble = e.optDouble("stalls", 0.0) +
            2.0 * e.optDouble("errors", 0.0) +
            e.optDouble("startSec", 0.0) / 3.0
        val weight = plays / (plays + 3.0)
        return weight * (trouble / minutes) + (1 - weight) * UNKNOWN
    }

    companion object {
        const val EXPLORE = 0.1
        const val UNKNOWN = 1.0
        const val DECAY = 0.9
        const val PROMOTE_MIN_PLAYS = 3.0
        const val PROMOTE_MARGIN = 0.7
    }
}

// Starting preset until the learner has evidence. Pure rules, so they can be tested without a phone.
fun coldStartPreset(net: NetKind, kind: String, lowRam: Boolean): String = when {
    lowRam -> "low_memory"
    kind == "stream" && net == NetKind.CELLULAR -> "weak_network"
    kind == "stream" || (kind == "video" && net == NetKind.CELLULAR) -> "smooth_iptv"
    else -> VoidPresets.DEFAULT_ID
}
