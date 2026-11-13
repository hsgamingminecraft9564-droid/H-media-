package com.hmedia.player

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore

// Music library with artist and album names. Kept apart from MediaSearch so the video query is unchanged.
object MusicIndex {
    fun load(context: Context): List<MediaItem> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        return try {
            val cursor = context.contentResolver.query(
                collection,
                arrayOf(
                    MediaStore.Audio.Media._ID,
                    MediaStore.Audio.Media.DISPLAY_NAME,
                    MediaStore.Audio.Media.TITLE,
                    MediaStore.Audio.Media.ARTIST,
                    MediaStore.Audio.Media.ALBUM
                ),
                null,
                null,
                "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"
            ) ?: return emptyList()
            cursor.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
                val titleCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = c.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val out = ArrayList<MediaItem>(c.count)
                while (c.moveToNext()) {
                    val title = c.getString(titleCol)?.takeIf { it.isNotBlank() } ?: c.getString(nameCol) ?: ""
                    out += MediaItem(
                        title = title,
                        uri = ContentUris.withAppendedId(collection, c.getLong(idCol)),
                        isVideo = false,
                        artist = c.getString(artistCol) ?: "",
                        album = c.getString(albumCol) ?: ""
                    )
                }
                out
            }
        } catch (e: RuntimeException) {
            emptyList()
        }
    }
}
