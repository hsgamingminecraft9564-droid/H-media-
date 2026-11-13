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
import androidx.compose.ui.res.painterResource
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlinx.coroutines.delay
import android.view.WindowManager

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

    // Landscape hides the status and navigation bars so the picture uses the whole screen. They return in portrait and when leaving.
    DisposableEffect(landscape) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            if (landscape) controller.hide(WindowInsetsCompat.Type.systemBars()) else controller.show(WindowInsetsCompat.Type.systemBars())
        }
        onDispose {
            window?.let { WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    // Keep the screen on while a video or channel is playing, so it does not switch off during a film.
    // Music may still play with the screen off.
    val keepScreenOn = snap.playWhenReady && !isAudioNow
    DisposableEffect(keepScreenOn) {
        val window = activity?.window
        if (keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

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
                LandscapePlayer(engine, isAudio, artwork, snap.playWhenReady, snap.positionMs, snap.durationMs, onBack)
            } else if (!isAudio) {
                // Portrait video: the picture on top with its controls on it, the title and modes below
                Column(Modifier.fillMaxSize()) {
                    LandscapePlayer(
                        engine, isAudio, artwork, snap.playWhenReady, snap.positionMs, snap.durationMs, onBack,
                        Modifier.fillMaxWidth().height(260.dp),
                        autoHide = false
                    )
                    Column(
                        Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text(Playback.title, style = MaterialTheme.typography.titleMedium, maxLines = 2)
                        Text(Playback.status, style = MaterialTheme.typography.bodySmall)
                        PlayerModesAndSpeed()
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

// Landscape player: the picture fills the screen. Controls appear on tap and hide after a few seconds while playing.
@Composable
private fun LandscapePlayer(
    engine: VoidEngine,
    isAudio: Boolean,
    artwork: ImageBitmap?,
    playWhenReady: Boolean,
    positionMs: Long,
    durationMs: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier.fillMaxSize(),
    autoHide: Boolean = true
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var wakeTick by remember { mutableStateOf(0) }
    var dragValue by remember { mutableStateOf<Float?>(null) }

    // Every tap and every control press restarts the timer that hides the controls
    LaunchedEffect(wakeTick, playWhenReady) {
        controlsVisible = true
        if (playWhenReady && autoHide) {
            delay(3500)
            controlsVisible = false
        }
    }

    Box(
        modifier.background(Color.Black).clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null
        ) {
            if (controlsVisible) {
                controlsVisible = false
            } else {
                wakeTick++
            }
        }
    ) {
        MediaSurface(engine, isAudio, artwork, Modifier.fillMaxSize())
        AnimatedVisibility(visible = controlsVisible, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize().background(Color(0x80000000))) {
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                    Text(Playback.title, maxLines = 1, color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(28.dp)
                ) {
                    IconButton(onClick = {
                        Playback.previous()
                        wakeTick++
                    }) {
                        Icon(painterResource(R.drawable.ic_previous), contentDescription = stringResource(R.string.player_previous), tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = {
                        engine.togglePlayPause()
                        wakeTick++
                    }) {
                        Icon(
                            painterResource(if (playWhenReady) R.drawable.ic_pause else R.drawable.ic_play),
                            contentDescription = stringResource(if (playWhenReady) R.string.pause else R.string.play),
                            tint = Color.White,
                            modifier = Modifier.size(56.dp)
                        )
                    }
                    IconButton(onClick = {
                        Playback.next()
                        wakeTick++
                    }) {
                        Icon(painterResource(R.drawable.ic_next), contentDescription = stringResource(R.string.player_next), tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
                if (durationMs > 0) {
                    val shown = (dragValue ?: positionMs.coerceIn(0L, durationMs).toFloat()).coerceIn(0f, durationMs.toFloat())
                    Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                        Slider(
                            value = shown,
                            onValueChange = {
                                dragValue = it
                                wakeTick++
                            },
                            onValueChangeFinished = {
                                dragValue?.let { engine.seekTo(it.toLong()) }
                                dragValue = null
                            },
                            valueRange = 0f..durationMs.toFloat()
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(clock(shown.toLong()), color = Color.White, style = MaterialTheme.typography.bodySmall)
                            Text(clock(durationMs), color = Color.White, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayerHeader(onBack: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back)) }
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
        // The picture takes the shape of the video (widescreen, portrait, square), so the whole
        // frame shows with black bars where needed, as in other video players.
        Box(modifier.background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c -> SurfaceView(c).also { engine.attachVideoView(it) } }
            )
        }
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
            TextButton(onClick = { Playback.previous() }) {
                Icon(painterResource(R.drawable.ic_previous), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.player_previous))
            }
            Button(onClick = { engine.togglePlayPause() }) {
                Icon(
                    painterResource(if (playWhenReady) R.drawable.ic_pause else R.drawable.ic_play),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(stringResource(if (playWhenReady) R.string.pause else R.string.play))
            }
            TextButton(onClick = { Playback.next() }) {
                Icon(painterResource(R.drawable.ic_next), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(R.string.player_next))
            }
        }

        PlayerModesAndSpeed()
    }
}

// Shuffle, repeat and speed. Shown under the picture in portrait video, and at the bottom of the controls on audio.
@Composable
private fun PlayerModesAndSpeed() {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { Playback.toggleShuffle() }) {
                Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(stringResource(if (Playback.shuffle) R.string.player_shuffle_on else R.string.player_shuffle_off))
            }
            TextButton(onClick = { Playback.cycleRepeat() }) {
                Icon(painterResource(R.drawable.ic_repeat), contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
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
