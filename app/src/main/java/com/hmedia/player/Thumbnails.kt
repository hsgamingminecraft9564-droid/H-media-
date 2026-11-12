package com.hmedia.player

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// Recent thumbnails kept in memory so scrolling does not reload them. Limited to 1/16 of the app's memory.
private object ThumbCache {
    private val maxKb = (Runtime.getRuntime().maxMemory() / 1024L / 16L).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxKb) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun get(key: String): Bitmap? = cache.get(key)

    fun put(key: String, bitmap: Bitmap) {
        cache.put(key, bitmap)
    }
}

// Android's media store makes thumbnails from Android 10 (API 29). Older versions show the banner.
@RequiresApi(29)
private fun readThumbnail(context: Context, uri: Uri): Bitmap? =
    runCatching { context.contentResolver.loadThumbnail(uri, Size(320, 180), null) }.getOrNull()

// Returns the thumbnail of a video or audio file, or null when there is none.
suspend fun loadThumbnail(context: Context, uri: Uri): Bitmap? {
    val key = uri.toString()
    ThumbCache.get(key)?.let { return it }
    if (Build.VERSION.SDK_INT < 29) return null
    return withContext(Dispatchers.IO) {
        readThumbnail(context, uri)?.also { ThumbCache.put(key, it) }
    }
}

// Shows the file's thumbnail. Until it loads, or when there is none (audio without art, older
// Android), it shows the red banner instead.
@Composable
fun ThumbOrBanner(uri: Uri, modifier: Modifier) {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(initialValue = null, key1 = uri) {
        value = loadThumbnail(context, uri)
    }
    val bitmap = thumb
    if (bitmap != null) {
        Image(
            bitmap = bitmap.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        BannerBackground(modifier)
    }
}
