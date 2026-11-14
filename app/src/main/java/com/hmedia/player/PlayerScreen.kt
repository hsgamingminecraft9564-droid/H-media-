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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp

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
fun PlayerScreen(engine: VoidEngine, onSearch: () -> Unit = {}, onBack: () -> Unit) {
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


    var showQueue by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }

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
                LandscapePlayer(
                    engine, isAudio, artwork, snap.playWhenReady, snap.positionMs, snap.durationMs, onBack,
                    onQueue = { showQueue = true }, onSettings = { showSettings = true }
                )
            } else {
                PortraitPlayer(
                    engine, isAudio, artwork, snap.playWhenReady, snap.positionMs, snap.durationMs,
                    onBack, onSearch, onQueue = { showQueue = true }, onSettings = { showSettings = true }
                )
            }
        }
        if (showQueue) QueueSheet { showQueue = false }
        if (showSettings) PlayerSettingsSheet { showSettings = false }
    }
}

private fun speedLabel(s: Float): String = if (s == s.toInt().toFloat()) "${s.toInt()}x" else "${s}x"

private val PillFill = Color(0xCC101014)
private val PillBorder = Color(0x26FFFFFF)

// Portrait player, laid out as in the concept art: top bar, title and status, the cover (or the
// video), seek bar, the five transport buttons, the speed and playlist pills and the bottom bar.
@Composable
private fun PortraitPlayer(
    engine: VoidEngine,
    isAudio: Boolean,
    artwork: ImageBitmap?,
    playWhenReady: Boolean,
    positionMs: Long,
    durationMs: Long,
    onBack: () -> Unit,
    onSearch: () -> Unit,
    onQueue: () -> Unit,
    onSettings: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    Column(Modifier.fillMaxSize()) {
        PlayerTopBar(onBack, onSearch, onQueue, onSettings)
        Column(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            Text(
                Playback.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painterResource(R.drawable.ic_equalizer),
                    contentDescription = null,
                    tint = if (playWhenReady) accent else MutedText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    Playback.status,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (playWhenReady) accent else MutedText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(16.dp))
            MediaSurface(engine, isAudio, artwork, Modifier.fillMaxWidth().weight(1f))
            Spacer(Modifier.height(10.dp))
            PlayerSeek(engine, positionMs, durationMs)
            Spacer(Modifier.height(4.dp))
            PlayerTransport(engine, playWhenReady, 76.dp)
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SpeedPill()
                PillButton(onClick = onQueue) {
                    Icon(painterResource(R.drawable.ic_playlist), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.player_playlist), style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                }
            }
            Spacer(Modifier.height(10.dp))
        }
        PlayerBottomBar(onQueue, onSettings)
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
    onQueue: () -> Unit,
    onSettings: () -> Unit
) {
    var controlsVisible by remember { mutableStateOf(true) }
    var wakeTick by remember { mutableStateOf(0) }

    // Every tap and every control press restarts the timer that hides the controls
    LaunchedEffect(wakeTick, playWhenReady) {
        controlsVisible = true
        if (playWhenReady) {
            delay(3500)
            controlsVisible = false
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(
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
            Box(Modifier.fillMaxSize().background(Color(0x99000000))) {
                Row(
                    Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                    Text(
                        Playback.title,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    IconButton(onClick = { wakeTick++; onQueue() }) {
                        Icon(painterResource(R.drawable.ic_playlist), contentDescription = stringResource(R.string.player_playlist), tint = Color.White)
                    }
                    IconButton(onClick = { wakeTick++; onSettings() }) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.player_settings), tint = Color.White)
                    }
                }
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(36.dp)
                ) {
                    IconButton(onClick = { Playback.previous(); wakeTick++ }) {
                        Icon(painterResource(R.drawable.ic_previous), contentDescription = stringResource(R.string.player_previous), tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    PlayRing(playWhenReady, 76.dp) { engine.togglePlayPause(); wakeTick++ }
                    IconButton(onClick = { Playback.next(); wakeTick++ }) {
                        Icon(painterResource(R.drawable.ic_next), contentDescription = stringResource(R.string.player_next), tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }
                Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    PlayerSeek(engine, positionMs, durationMs) { wakeTick++ }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        val accent = MaterialTheme.colorScheme.primary
                        IconButton(onClick = { Playback.toggleShuffle(); wakeTick++ }) {
                            Icon(
                                painterResource(R.drawable.ic_shuffle),
                                contentDescription = stringResource(if (Playback.shuffle) R.string.player_shuffle_on else R.string.player_shuffle_off),
                                tint = if (Playback.shuffle) accent else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        IconButton(onClick = { Playback.cycleRepeat(); wakeTick++ }) {
                            Icon(
                                painterResource(R.drawable.ic_repeat),
                                contentDescription = stringResource(repeatLabel(Playback.repeat)),
                                tint = if (Playback.repeat != RepeatMode.OFF) accent else Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        SpeedPill { wakeTick++ }
                    }
                }
            }
        }
    }
}

// Logo and name, search and the three dots, as in the concept art. Tapping the logo shrinks the player.
@Composable
private fun PlayerTopBar(onBack: () -> Unit, onSearch: () -> Unit, onQueue: () -> Unit, onSettings: () -> Unit) {
    val red = MaterialTheme.colorScheme.primary
    val title = stringResource(R.string.home_title)
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 22.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            painterResource(R.drawable.hmp_logo),
            contentDescription = stringResource(R.string.back),
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(8.dp)).clickable { onBack() }
        )
        Spacer(Modifier.width(14.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = red, fontWeight = FontWeight.Bold)) { append(title.take(1)) }
                append(title.drop(1))
            },
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onSearch) {
            Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.search_title), tint = Color.White)
        }
        Box {
            IconButton(onClick = { menu = true }) { MoreDots() }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.player_minimize)) }, onClick = { menu = false; onBack() })
                DropdownMenuItem(text = { Text(stringResource(R.string.player_playlist)) }, onClick = { menu = false; onQueue() })
                DropdownMenuItem(text = { Text(stringResource(R.string.player_settings)) }, onClick = { menu = false; onSettings() })
            }
        }
    }
}

// Thin red seek bar with a round handle, and the times under it. Hidden for live streams.
@Composable
private fun PlayerSeek(engine: VoidEngine, positionMs: Long, durationMs: Long, onInteract: () -> Unit = {}) {
    if (durationMs <= 0) return
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val max = durationMs.toFloat()
    val shown = (dragValue ?: positionMs.coerceIn(0L, durationMs).toFloat()).coerceIn(0f, max)
    Column(Modifier.fillMaxWidth()) {
        SeekBar(
            value = shown,
            max = max,
            onChange = { dragValue = it; onInteract() },
            onFinish = {
                dragValue?.let { engine.seekTo(it.toLong()) }
                dragValue = null
            }
        )
        Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(clock(shown.toLong()), style = MaterialTheme.typography.bodyMedium, color = Color.White)
            Text(clock(durationMs), style = MaterialTheme.typography.bodyMedium, color = Color.White)
        }
    }
}

@Composable
private fun SeekBar(value: Float, max: Float, onChange: (Float) -> Unit, onFinish: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    var widthPx by remember { mutableStateOf(1f) }
    val fraction = if (max > 0f) (value / max).coerceIn(0f, 1f) else 0f
    Box(
        Modifier.fillMaxWidth().height(28.dp).padding(horizontal = 8.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(max) {
                detectTapGestures { o ->
                    onChange((o.x / widthPx).coerceIn(0f, 1f) * max)
                    onFinish()
                }
            }
            .pointerInput(max) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> onChange((o.x / widthPx).coerceIn(0f, 1f) * max) },
                    onDragEnd = { onFinish() },
                    onDragCancel = { onFinish() },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        onChange((change.position.x / widthPx).coerceIn(0f, 1f) * max)
                    }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val track = 3.dp.toPx()
            val x = size.width * fraction
            val round = CornerRadius(track / 2f, track / 2f)
            drawRoundRect(Color(0x33FFFFFF), topLeft = Offset(0f, cy - track / 2f), size = androidx.compose.ui.geometry.Size(size.width, track), cornerRadius = round)
            drawRoundRect(accent, topLeft = Offset(0f, cy - track / 2f), size = androidx.compose.ui.geometry.Size(x, track), cornerRadius = round)
            drawCircle(accent.copy(alpha = 0.28f), radius = 11.dp.toPx(), center = Offset(x, cy))
            drawCircle(accent, radius = 7.dp.toPx(), center = Offset(x, cy))
        }
    }
}

// Shuffle, previous, the big play ring, next and repeat
@Composable
private fun PlayerTransport(engine: VoidEngine, playWhenReady: Boolean, big: Dp) {
    val accent = MaterialTheme.colorScheme.primary
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { Playback.toggleShuffle() }) {
            Icon(
                painterResource(R.drawable.ic_shuffle),
                contentDescription = stringResource(if (Playback.shuffle) R.string.player_shuffle_on else R.string.player_shuffle_off),
                tint = if (Playback.shuffle) accent else Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
        IconButton(onClick = { Playback.previous() }) {
            Icon(painterResource(R.drawable.ic_previous), contentDescription = stringResource(R.string.player_previous), tint = Color.White, modifier = Modifier.size(36.dp))
        }
        PlayRing(playWhenReady, big) { engine.togglePlayPause() }
        IconButton(onClick = { Playback.next() }) {
            Icon(painterResource(R.drawable.ic_next), contentDescription = stringResource(R.string.player_next), tint = Color.White, modifier = Modifier.size(36.dp))
        }
        IconButton(onClick = { Playback.cycleRepeat() }) {
            Icon(
                painterResource(R.drawable.ic_repeat),
                contentDescription = stringResource(repeatLabel(Playback.repeat)),
                tint = if (Playback.repeat != RepeatMode.OFF) accent else Color.White,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

// The big round play/pause button with a red ring
@Composable
private fun PlayRing(playing: Boolean, size: Dp, onClick: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(
        Modifier.size(size)
            .shadow(14.dp, CircleShape, clip = false, ambientColor = accent, spotColor = accent)
            .clip(CircleShape)
            .background(Color(0xFF0B0B0F))
            .border(2.dp, accent, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painterResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = stringResource(if (playing) R.string.pause else R.string.play),
            tint = Color.White,
            modifier = Modifier.size(size * 0.45f)
        )
    }
}

@Composable
private fun PillButton(onClick: () -> Unit, content: @Composable RowScope.() -> Unit) {
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier.height(48.dp).clip(shape).background(PillFill).border(1.dp, PillBorder, shape)
            .clickable(onClick = onClick).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content
    )
}

// "1x" with a small arrow; opens the list of speeds
@Composable
private fun SpeedPill(onInteract: () -> Unit = {}) {
    var open by remember { mutableStateOf(false) }
    Box {
        PillButton(onClick = { open = true; onInteract() }) {
            Icon(painterResource(R.drawable.ic_speed), contentDescription = stringResource(R.string.player_speed), tint = Color.White, modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(12.dp))
            Text(speedLabel(Playback.playbackSpeed), style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
            Spacer(Modifier.width(10.dp))
            Canvas(Modifier.size(width = 12.dp, height = 7.dp)) {
                val arrow = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width / 2f, size.height)
                    close()
                }
                drawPath(arrow, Color.White)
            }
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SPEEDS.forEach { s ->
                DropdownMenuItem(
                    text = {
                        Text(
                            speedLabel(s),
                            color = if (Playback.playbackSpeed == s) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = { Playback.setSpeed(s); open = false; onInteract() }
                )
            }
        }
    }
}

// The curved bar at the bottom with a red glowing edge: Playlist and Settings
@Composable
private fun PlayerBottomBar(onQueue: () -> Unit, onSettings: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Box(Modifier.fillMaxWidth().height(84.dp)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            val edge = Path().apply {
                moveTo(0f, h * 0.62f)
                cubicTo(w * 0.10f, h * 0.30f, w * 0.20f, h * 0.10f, w * 0.45f, h * 0.20f)
                cubicTo(w * 0.70f, h * 0.30f, w * 0.85f, h * 0.15f, w, h * 0.05f)
            }
            val fill = Path().apply {
                addPath(edge)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(fill, Color(0xFF0B0B0F))
            drawPath(edge, accent.copy(alpha = 0.25f), style = Stroke(width = 8.dp.toPx()))
            drawPath(edge, accent, style = Stroke(width = 2.dp.toPx()))
        }
        Row(Modifier.fillMaxSize().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            BarTab(R.drawable.ic_playlist, stringResource(R.string.player_playlist), true, Modifier.weight(1f), onQueue)
            Box(Modifier.width(1.dp).height(30.dp).background(Color(0x33FFFFFF)))
            BarTab(R.drawable.ic_settings, stringResource(R.string.nav_settings), false, Modifier.weight(1f), onSettings)
        }
    }
}

@Composable
private fun BarTab(icon: Int, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val color = if (selected) MaterialTheme.colorScheme.primary else MutedText
    Row(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.titleMedium, color = color, maxLines = 1)
    }
}

// The playlist: every track in play order; tap one to jump to it
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun QueueSheet(onDismiss: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    val current = Playback.title // read so the list follows the track that is playing
    val items = remember(current) { Playback.queueEntries() }
    val position = remember(current) { Playback.queuePosition }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF111115),
        contentColor = Color.White
    ) {
        Text(
            stringResource(R.string.player_playlist),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        if (items.isEmpty()) {
            Text(
                stringResource(R.string.player_playlist_empty),
                color = MutedText,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp)
            )
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                itemsIndexed(items) { index, item ->
                    val now = index == position
                    Row(
                        Modifier.fillMaxWidth().clickable { Playback.playAt(index) }.padding(horizontal = 24.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (now) {
                            Icon(painterResource(R.drawable.ic_equalizer), contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
                        } else {
                            Text("${index + 1}", color = MutedText, modifier = Modifier.width(20.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                item.title,
                                color = if (now) accent else Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (item.artist.isNotBlank()) {
                                Text(item.artist, color = MutedText, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

// Shuffle, repeat and speed as pill choices
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlayerSettingsSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF111115),
        contentColor = Color.White
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text(stringResource(R.string.player_settings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))
            SettingsLabel(R.string.player_shuffle)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OptionChip(stringResource(R.string.player_shuffle_off), !Playback.shuffle, Modifier.weight(1f)) {
                    if (Playback.shuffle) Playback.toggleShuffle()
                }
                OptionChip(stringResource(R.string.player_shuffle_on), Playback.shuffle, Modifier.weight(1f)) {
                    if (!Playback.shuffle) Playback.toggleShuffle()
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsLabel(R.string.player_repeat)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RepeatMode.values().forEach { mode ->
                    OptionChip(stringResource(repeatLabel(mode)), Playback.repeat == mode, Modifier.weight(1f)) {
                        Playback.applyRepeat(mode)
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            SettingsLabel(R.string.player_speed)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SPEEDS.forEach { s ->
                    OptionChip(speedLabel(s), Playback.playbackSpeed == s, Modifier.weight(1f)) { Playback.setSpeed(s) }
                }
            }
        }
    }
}

@Composable
private fun SettingsLabel(res: Int) {
    Text(stringResource(res), style = MaterialTheme.typography.bodySmall, color = MutedText, modifier = Modifier.padding(bottom = 8.dp))
}

@Composable
private fun OptionChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.height(44.dp).clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else PillFill)
            .border(1.dp, if (selected) Color.Transparent else PillBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 6.dp))
    }
}

// The video picture, or the cover art for music
@Composable
private fun MediaSurface(engine: VoidEngine, isAudio: Boolean, artwork: ImageBitmap?, modifier: Modifier) {
    if (isAudio) {
        BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth, maxHeight, 460.dp)
            Box(
                Modifier.size(side).clip(RoundedCornerShape(28.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF2A0A10), Color(0xFF0B0B0F))))
                    .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (artwork != null) {
                    Image(
                        bitmap = artwork,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        painterResource(R.drawable.ic_music),
                        contentDescription = null,
                        tint = Color(0x80FFFFFF),
                        modifier = Modifier.size(side * 0.3f)
                    )
                }
            }
        }
    } else {
        // The picture takes the shape of the video (widescreen, portrait, square), so the whole
        // frame shows with black bars where needed, as in other video players.
        Box(modifier.clip(RoundedCornerShape(20.dp)).background(Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { c -> SurfaceView(c).also { engine.attachVideoView(it) } }
            )
        }
    }
}
