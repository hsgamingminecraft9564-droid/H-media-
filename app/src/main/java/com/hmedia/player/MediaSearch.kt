package com.hmedia.player

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

data class MediaItem(val title: String, val uri: Uri, val isVideo: Boolean, val artist: String = "", val album: String = "", val durationMs: Long = 0L)

object MediaSearch {
    // The media permissions this Android version needs
    fun requiredPermissions(): Array<String> =
        if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    // True once the user has allowed any media access. Android remembers the choice, so the app
    // checks it instead of asking again on every visit.
    fun hasAccess(context: Context): Boolean =
        requiredPermissions().any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    // Every video or every audio file on the phone, sorted by name
    fun list(context: Context, isVideo: Boolean): List<MediaItem> = query(context, isVideo)

    // Files whose names contain the text, for both video and audio. The match is made here rather
    // than in the database query, so it behaves the same on every Android version.
    fun search(context: Context, text: String): List<MediaItem> {
        val needle = text.trim()
        if (needle.isEmpty()) return emptyList()
        return (query(context, true) + query(context, false))
            .filter { it.title.contains(needle, ignoreCase = true) }
    }

    private fun query(context: Context, isVideo: Boolean): List<MediaItem> {
        val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                         else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        // A missing permission makes the query throw. That type is skipped instead of crashing the app.
        return try {
            val cursor = context.contentResolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DISPLAY_NAME),
                null,
                null,
                "${MediaStore.MediaColumns.DISPLAY_NAME} COLLATE NOCASE ASC"
            ) ?: return emptyList()
            cursor.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val out = ArrayList<MediaItem>(c.count)
                while (c.moveToNext()) {
                    out += MediaItem(
                        title = c.getString(nameCol) ?: "",
                        uri = ContentUris.withAppendedId(collection, c.getLong(idCol)),
                        isVideo = isVideo
                    )
                }
                out
            }
        } catch (e: RuntimeException) {
            emptyList()
        }
    }
}
