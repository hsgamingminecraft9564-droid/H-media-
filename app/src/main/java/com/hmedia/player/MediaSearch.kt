package com.hmedia.player

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore

data class MediaItem(val title: String, val uri: Uri, val isVideo: Boolean)

object MediaSearch {
    // Finds video and audio files whose names match, using Android's media index.
    // Call from a background thread.
    fun search(context: Context, query: String): List<MediaItem> {
        val out = mutableListOf<MediaItem>()
        // Escape the SQL wildcards so a typed % or _ is searched as plain text
        val escaped = query.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        val pattern = "%$escaped%"
        for (isVideo in listOf(true, false)) {
            val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                             else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            // If this media type was not granted, the query throws; skip that type instead of crashing
            val cursor = runCatching {
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} LIKE ? ESCAPE '\\'",
                    arrayOf(pattern),
                    "${MediaStore.MediaColumns.DISPLAY_NAME} ASC"
                )
            }.getOrNull() ?: continue
            cursor.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                while (c.moveToNext()) {
                    out += MediaItem(
                        title = c.getString(nameCol) ?: "",
                        uri = ContentUris.withAppendedId(collection, c.getLong(idCol)),
                        isVideo = isVideo
                    )
                }
            }
        }
        return out
    }
}
