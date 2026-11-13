package com.hmedia.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// Copies the picture chosen from the gallery into the app, scaled to at most 1440 pixels on its longest side.
// The app keeps its own copy, so the background stays after a restart and draws quickly.
fun saveBackground(ctx: Context, uri: Uri): String? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val full = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("The picture could not be read.")
    val longest = maxOf(full.width, full.height)
    val scaled = if (longest > 1440) {
        val factor = 1440f / longest
        Bitmap.createScaledBitmap(full, (full.width * factor).toInt(), (full.height * factor).toInt(), true)
    } else {
        full
    }
    val file = File(ctx.filesDir, "background.jpg")
    file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }
    file.absolutePath
}.getOrNull()

// The chosen picture behind the whole app, dimmed so text stays readable.
// While a video from the phone plays, the video backdrop is shown instead.
@Composable
fun AppBackground(prefs: AppPrefs) {
    val path = prefs.backgroundPath
    val picture by produceState<ImageBitmap?>(initialValue = null, key1 = path) {
        value = if (path.isEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
        }
    }
    val videoBackdropShowing = Playback.kind == "video" && Playback.currentUri?.scheme == "content"
    val image = picture
    if (image != null && !videoBackdropShowing) {
        Box(Modifier.fillMaxSize()) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.fillMaxSize().background(Color(0xB30B0B0F)))
        }
    }
}
