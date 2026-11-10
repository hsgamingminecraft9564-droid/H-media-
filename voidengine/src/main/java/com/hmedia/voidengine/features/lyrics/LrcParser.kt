package com.hmedia.voidengine.features.lyrics

data class LyricLine(val timeMs: Long, val text: String)

class LyricsTimeline(lines: List<LyricLine>) {
    val lines: List<LyricLine> = lines.sortedBy { it.timeMs }

    /** Index of the line that is current at [positionMs], or -1 before the first line. */
    fun indexAt(positionMs: Long): Int {
        var low = 0
        var high = lines.size - 1
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    fun isEmpty(): Boolean = lines.isEmpty()
}

/**
 * Parses .lrc text: "[mm:ss.xx] text", several timestamps per line, and "[offset:+/-ms]".
 * Usage with the engine:  engine.snapshot.map { timeline.indexAt(it.positionMs) }.distinctUntilChanged()
 */
object LrcParser {
    private val timeTag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val offsetTag = Regex("^\\[offset:\\s*([+-]?\\d+)\\s*]", RegexOption.IGNORE_CASE)

    fun parse(text: String): LyricsTimeline {
        var offsetMs = 0L
        val result = ArrayList<LyricLine>()
        for (raw in text.removePrefix("\uFEFF").lines()) {
            val line = raw.trim()
            if (line.isEmpty()) continue

            val offset = offsetTag.find(line)
            if (offset != null) {
                offsetMs = offset.groupValues[1].removePrefix("+").toLongOrNull() ?: 0L
                continue
            }

            val times = ArrayList<Long>()
            var index = 0
            while (true) {
                val match = timeTag.find(line, index)
                if (match == null || match.range.first != index) break
                val minutes = match.groupValues[1].toLong()
                val seconds = match.groupValues[2].toLong()
                val fraction = match.groupValues[3]
                val fractionMs = when (fraction.length) {
                    0 -> 0L
                    1 -> fraction.toLong() * 100L
                    2 -> fraction.toLong() * 10L
                    else -> fraction.toLong()
                }
                times.add(minutes * 60_000L + seconds * 1_000L + fractionMs)
                index = match.range.last + 1
            }
            if (times.isEmpty()) continue

            val lyric = line.substring(index).trim()
            for (t in times) {
                result.add(LyricLine((t - offsetMs).coerceAtLeast(0L), lyric))
            }
        }
        return LyricsTimeline(result)
    }
}
