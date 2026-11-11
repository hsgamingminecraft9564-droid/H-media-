package com.hmedia.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import android.os.Build
import android.util.Size
import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hmedia.voidengine.VoidEngine
import com.hmedia.voidengine.togglePlayPause
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val SPEEDS = listOf(1f, 1.25f, 1.5f, 2f)

// m:ss, or h:mm:ss for long files
private fun clock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

private fun repeatLabel(mode: RepeatMode): Int = when (mode) {
    RepeatMode.OFF -> R.string.player_repeat_off
    RepeatMode.ALL -> R.string.player_repeat_all
    RepeatMode.ONE -> R.string.player_repeat_one
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

// Player: video picture or music artwork, a blurred artwork background, and real controls.
// Portrait stacks the picture above the controls. Landscape puts them side by side, so the picture
// keeps most of the height.
@Composable
fun PlayerScreen(engine: VoidEngine, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    val lifecycleOwner = LocalLifecycleOwner.current
    val snap by engine.snapshot.collectAsState()
    val uri = Playback.currentUri
    val isAudio = Playback.kind == "audio"
    // The effects below outlive one composition, so they read the latest value through this
    val isAudioNow by rememberUpdatedState(isAudio)
    var artwork by remember { mutableStateOf<ImageBitmap?>(null) }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

    BackHandler { onBack() }

    // Thumbnail of the current item, loaded off the main thread (Android 10 and newer). The previous
    // picture stays until the new one is ready or known to be missing. If the item changes again
    // first, this effect is cancelled and a late result is never shown.
    LaunchedEffect(uri) {
        val loaded = if (uri != null && Build.VERSION.SDK_INT >= 29) {
            withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.loadThumbnail(uri, Size(512, 512), null).asImageBitmap() }
                    .getOrNull()
            }
        } else {
            null
        }
        artwork = loaded
    }

    // Leaving the screen removes the picture. Video pauses; music keeps playing. Rotating the phone
    // is not leaving: the picture is re-attached and playback carries on.
    DisposableEffect(engine) {
        onDispose {
            if (!isAudioNow) {
                engine.detachVideoView()
                if (activity?.isChangingConfigurations != true) engine.pause()
            }
        }
    }

    // Video pauses when the app goes to the background, but not when the phone is only rotating
    DisposableEffect(engine, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && !isAudioNow && activity?.isChangingConfigurations != true) {
                engine.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(Modifier.fillMaxSize()) {
        Crossfade(targetState = artwork, animationSpec = tween(350)) { bitmap ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().blur(28.dp)
                )
            }
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(listOf(Color(0xE6000000), Color(0xF2000000)))
            )
        )

        // The background is always dark, so the text on it is always light
        CompositionLocalProvider(LocalContentColor provides Color.White) {
            if (landscape) {
                Row(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    MediaSurface(engine, isAudio, artwork, Modifier.weight(1.4f).fillMaxHeight())
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                        PlayerHeader(onBack)
                        PlayerTransport(engine, snap.playWhenReady, snap.positionMs, snap.durationMs)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            } else {
                Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                    PlayerHeader(onBack)
                    MediaSurface(engine, isAudio, artwork, Modifier.fillMaxWidth().weight(1f))
                    PlayerTransport(engine, snap.playWhenReady, snap.positionMs, snap.durationMs)
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
    }
}

@Composable
private fun PlayerHeader(onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(
            Playback.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            Playback.status,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

// The video picture, or the cover art for music
@Composable
private fun MediaSurface(engine: VoidEngine, isAudio: Boolean, artwork: ImageBitmap?, modifier: Modifier) {
    if (isAudio) {
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight, 320.dp)
            if (artwork != null) {
                Image(
                    bitmap = artwork,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(side).clip(RoundedCornerShape(16.dp))
                )
            } else {
                Text(
                    stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        AndroidView(
            modifier = modifier,
            factory = { c -> SurfaceView(c).also { engine.attachVideoView(it) } }
        )
    }
}

// Seek bar, play/pause, previous/next, shuffle, repeat and speed. Rows are short so they fit a
// narrow landscape panel and larger text sizes.
@Composable
private fun PlayerTransport(engine: VoidEngine, playWhenReady: Boolean, positionMs: Long, durationMs: Long) {
    var dragValue by remember { mutableStateOf<Float?>(null) }

    Column(Modifier.fillMaxWidth()) {
        if (durationMs > 0) {
            val shown = (dragValue ?: positionMs.coerceIn(0L, durationMs).toFloat())
                .coerceIn(0f, durationMs.toFloat())
            Slider(
                value = shown,
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { engine.seekTo(it.toLong()) }
                    dragValue = null
                },
                valueRange = 0f..durationMs.toFloat()
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(clock(shown.toLong()), style = MaterialTheme.typography.bodySmall)
                Text(clock(durationMs), style = MaterialTheme.typography.bodySmall)
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { Playback.previous() }) { Text(stringResource(R.string.player_previous)) }
            Button(onClick = { engine.togglePlayPause() }) {
                Text(stringResource(if (playWhenReady) R.string.pause else R.string.play))
            }
            TextButton(onClick = { Playback.next() }) { Text(stringResource(R.string.player_next)) }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { Playback.toggleShuffle() }) {
                Text(stringResource(if (Playback.shuffle) R.string.player_shuffle_on else R.string.player_shuffle_off))
            }
            TextButton(onClick = { Playback.cycleRepeat() }) {
                Text(stringResource(repeatLabel(Playback.repeat)))
            }
        }

        Text(
            stringResource(R.string.player_speed),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 8.dp)
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            SPEEDS.forEach { s ->
                val label = if (s == 1f) "1x" else "${s}x"
                val padding = PaddingValues(horizontal = 4.dp)
                if (Playback.playbackSpeed == s) {
                    Button(
                        onClick = { Playback.setSpeed(s) },
                        modifier = Modifier.weight(1f),
                        contentPadding = padding
                    ) { Text(label, maxLines = 1) }
                } else {
                    TextButton(
                        onClick = { Playback.setSpeed(s) },
                        modifier = Modifier.weight(1f),
                        contentPadding = padding
                    ) { Text(label, maxLines = 1) }
                }
            }
        }
    }
}
