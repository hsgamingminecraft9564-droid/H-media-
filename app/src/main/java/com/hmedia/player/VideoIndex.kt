package com.hmedia.player

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

// Video library with each file's length and size. Kept apart from MediaSearch so the older query is unchanged.
object VideoIndex {
    fun load(context: Context): List<MediaItem> {
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        return try {
            val cursor = context.contentResolver.query(
                collection,
                arrayOf(
                    MediaStore.Video.Media._ID,
                    MediaStore.Video.Media.DISPLAY_NAME,
                    MediaStore.Video.Media.DURATION,
                    MediaStore.Video.Media.SIZE
                ),
                null,
                null,
                "${MediaStore.Video.Media.DISPLAY_NAME} COLLATE NOCASE ASC"
            ) ?: return emptyList()
            cursor.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
                val durationCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
                val out = ArrayList<MediaItem>(c.count)
                while (c.moveToNext()) {
                    out += MediaItem(
                        title = c.getString(nameCol) ?: "",
                        uri = ContentUris.withAppendedId(collection, c.getLong(idCol)),
                        isVideo = true,
                        durationMs = c.getLong(durationCol),
                        sizeBytes = c.getLong(sizeCol)
                    )
                }
                out
            }
        } catch (e: RuntimeException) {
            emptyList()
        }
    }
}

// m:ss, or h:mm:ss for long videos
fun durationLabel(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// 12 MB, 850 KB, 1.4 GB
fun sizeLabel(bytes: Long): String = when {
    bytes <= 0L -> ""
    bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
    bytes >= 1_000_000L -> "%d MB".format(bytes / 1_000_000L)
    else -> "%d KB".format(bytes / 1000L)
}
