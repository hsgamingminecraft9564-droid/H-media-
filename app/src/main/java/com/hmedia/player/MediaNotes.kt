package com.hmedia.player

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

// One private note or timestamp bookmark about a file. Stored only on this phone.
// positionMs is -1 for a plain note.
data class MemoryEntry(
    val id: String,
    val mediaKey: String,
    val mediaTitle: String,
    val positionMs: Long,
    val text: String,
    val createdAt: Long
)

object MediaNotes {
    private const val FILE = "media_notes.json"
    private const val LIMIT = 2000
    private val lock = Any()

    fun all(ctx: Context): List<MemoryEntry> = synchronized(lock) { read(ctx) }

    fun add(ctx: Context, mediaKey: String, mediaTitle: String, positionMs: Long, text: String): MemoryEntry {
        val entry = MemoryEntry(
            id = UUID.randomUUID().toString(),
            mediaKey = mediaKey,
            mediaTitle = mediaTitle.take(200),
            positionMs = positionMs,
            text = text.trim().take(500),
            createdAt = System.currentTimeMillis()
        )
        synchronized(lock) {
            val list = read(ctx).toMutableList()
            list.add(0, entry)
            write(ctx, list.take(LIMIT))
        }
        return entry
    }

    fun delete(ctx: Context, id: String) {
        synchronized(lock) { write(ctx, read(ctx).filterNot { it.id == id }) }
    }

    // A damaged file is ignored instead of crashing the app
    private fun read(ctx: Context): List<MemoryEntry> {
        val file = File(ctx.filesDir, FILE)
        if (!file.exists()) return emptyList()
        return try {
            val array = JSONArray(file.readText())
            val out = ArrayList<MemoryEntry>(array.length())
            for (i in 0 until array.length()) {
                val o = array.optJSONObject(i) ?: continue
                out += MemoryEntry(
                    id = o.optString("id"),
                    mediaKey = o.optString("mediaKey"),
                    mediaTitle = o.optString("mediaTitle"),
                    positionMs = o.optLong("positionMs", -1L),
                    text = o.optString("text"),
                    createdAt = o.optLong("createdAt")
                )
            }
            out.filter { it.id.isNotEmpty() && it.mediaKey.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Written to a temporary file first, so an interruption cannot leave a half-written file
    private fun write(ctx: Context, list: List<MemoryEntry>) {
        val array = JSONArray()
        for (e in list) {
            array.put(JSONObject().apply {
                put("id", e.id)
                put("mediaKey", e.mediaKey)
                put("mediaTitle", e.mediaTitle)
                put("positionMs", e.positionMs)
                put("text", e.text)
                put("createdAt", e.createdAt)
            })
        }
        val tmp = File(ctx.filesDir, "$FILE.tmp")
        tmp.writeText(array.toString())
        val target = File(ctx.filesDir, FILE)
        if (!tmp.renameTo(target)) {
            tmp.copyTo(target, overwrite = true)
            tmp.delete()
        }
    }
}
