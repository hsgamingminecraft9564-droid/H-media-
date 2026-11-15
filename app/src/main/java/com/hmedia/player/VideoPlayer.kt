/*
 * Copyright (c) 2026 Himanshu. All rights reserved.
 */

package com.hmedia.player

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.media.AudioManager
import android.view.SurfaceView
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hmedia.voidengine.PlaybackState
import com.hmedia.voidengine.PlayerSnapshot
import com.hmedia.voidengine.VoidEngine
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

// How the picture fills the screen: Fit keeps the whole picture, Fill zooms in to cover the screen
// (the edges are cut off), Stretch fills the screen and distorts the picture.
enum class FitMode { FIT, FILL, STRETCH }

// The video picture. The surface is given the exact shape of the video, so the picture is never squashed:
// a wide film in portrait has bars above and below, a tall phone video in landscape has bars at the sides.
@Composable
fun VideoSurface(engine: VoidEngine, videoWidth: Int, videoHeight: Int, mode: FitMode, modifier: Modifier) {
    BoxWithConstraints(modifier.background(Color.Black), contentAlignment = Alignment.Center) {
        val ratio = if (videoWidth > 0 && videoHeight > 0) videoWidth.toFloat() / videoHeight.toFloat() else 16f / 9f
        val boxRatio = maxWidth / maxHeight
        val wide = ratio > boxRatio
        val width = when (mode) {
            FitMode.STRETCH -> maxWidth
            FitMode.FIT -> if (wide) maxWidth else maxHeight * ratio
            FitMode.FILL -> if (wide) maxHeight * ratio else maxWidth
        }
        val height = when (mode) {
            FitMode.STRETCH -> maxHeight
            FitMode.FIT -> if (wide) maxWidth / ratio else maxHeight
            FitMode.FILL -> if (wide) maxHeight else maxWidth / ratio
        }
        AndroidView(
            modifier = Modifier.requiredSize(width, height),
            factory = { c -> SurfaceView(c).also { engine.attachVideoView(it) } }
        )
    }
}

// The video player, used in portrait and in landscape. Music has its own player screen.
// Tap shows or hides the controls. Swipe up or down on the left for brightness and on the right for volume,
// swipe sideways to seek, double-tap the left or right half to jump 10 seconds. The lock button turns it all off.
@Composable
fun VideoPlayer(
    engine: VoidEngine,
    snap: PlayerSnapshot,
    onBack: () -> Unit,
    onQueue: () -> Unit,
    onTools: () -> Unit,
    onPip: (() -> Unit)?
) {
    val ctx = LocalContext.current
    val activity = remember(ctx) { ctx.findActivity() }
    val audio = remember(ctx) { ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val accent = MaterialTheme.colorScheme.primary
    var controlsVisible by remember { mutableStateOf(true) }
    var wakeTick by remember { mutableStateOf(0) }
    var locked by remember { mutableStateOf(false) }
    var hud by remember { mutableStateOf("") }
    var hudTick by remember { mutableStateOf(0) }
    var fitIndex by rememberSaveable { mutableStateOf(0) }
    val fit = FitMode.values()[fitIndex % FitMode.values().size]
    val positionNow by rememberUpdatedState(snap.positionMs)
    val durationNow by rememberUpdatedState(snap.durationMs)
    val playing = snap.playWhenReady

    // Every tap and every control press restarts the timer that hides the controls
    LaunchedEffect(wakeTick, playing) {
        controlsVisible = true
        if (playing) {
            delay(3500)
            controlsVisible = false
        }
    }

    // The short message for a gesture disappears by itself
    LaunchedEffect(hudTick) {
        if (hud.isNotEmpty()) {
            delay(900)
            hud = ""
        }
    }

    // Leaving gives the brightness and the screen rotation back to the phone's own settings
    DisposableEffect(Unit) {
        onDispose {
            val window = activity?.window
            if (window != null) {
                val params = window.attributes
                params.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                window.attributes = params
            }
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    Box(
        Modifier.fillMaxSize().background(Color.Black)
            .pointerInput(locked) {
                detectTapGestures(
                    onTap = {
                        if (locked) {
                            wakeTick++
                        } else if (controlsVisible) {
                            controlsVisible = false
                        } else {
                            wakeTick++
                        }
                    },
                    onDoubleTap = { offset ->
                        if (!locked) {
                            val forward = offset.x > size.width / 2f
                            val top = if (durationNow > 0L) durationNow else Long.MAX_VALUE
                            val target = (positionNow + (if (forward) 10_000L else -10_000L)).coerceIn(0L, top)
                            engine.seekTo(target)
                            hud = if (forward) "+10 s" else "−10 s"
                            hudTick++
                        }
                    }
                )
            }
            .pointerInput(locked) {
                if (!locked) {
                    var leftSide = true
                    var level = 0f
                    detectVerticalDragGestures(
                        onDragStart = { start ->
                            leftSide = start.x < size.width / 2f
                            level = if (leftSide) brightnessOf(activity) else volumeOf(audio)
                        },
                        onVerticalDrag = { change, dragY ->
                            change.consume()
                            level = (level - dragY / size.height.toFloat() * 1.4f).coerceIn(0f, 1f)
                            if (leftSide) {
                                setBrightnessOf(activity, level)
                                hud = ctx.getString(R.string.hud_brightness, (level * 100f).roundToInt())
                            } else {
                                setVolumeOf(audio, level)
                                hud = ctx.getString(R.string.hud_volume, (level * 100f).roundToInt())
                            }
                            hudTick++
                        }
                    )
                }
            }
            .pointerInput(locked) {
                if (!locked) {
                    var target = 0L
                    detectHorizontalDragGestures(
                        onDragStart = { target = positionNow },
                        onDragEnd = { if (durationNow > 0L) engine.seekTo(target) },
                        onDragCancel = { },
                        onHorizontalDrag = { change, dragX ->
                            change.consume()
                            if (durationNow > 0L) {
                                target = (target + (dragX / size.width.toFloat() * 90_000f).toLong()).coerceIn(0L, durationNow)
                                hud = clock(target) + " / " + clock(durationNow)
                                hudTick++
                            }
                        }
                    )
                }
            }
    ) {
        VideoSurface(engine, snap.videoWidth, snap.videoHeight, fit, Modifier.fillMaxSize())

        if (snap.state == PlaybackState.BUFFERING || snap.state == PlaybackState.PREPARING) {
            CircularProgressIndicator(Modifier.align(Alignment.Center).size(48.dp), color = accent)
        }

        AnimatedVisibility(visible = controlsVisible && !locked, enter = fadeIn(), exit = fadeOut()) {
            Box(Modifier.fillMaxSize()) {
                // Top: back, title and details, then Picture-in-Picture, playlist and tools
                Row(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(start = 8.dp, end = 8.dp, top = 24.dp, bottom = 22.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back), tint = Color.White)
                    }
                    Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                        Text(
                            Playback.title,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        val details = (if (snap.videoWidth > 0 && snap.videoHeight > 0) "${snap.videoWidth}×${snap.videoHeight}  ·  " else "") +
                            speedLabel(Playback.playbackSpeed)
                        Text(details, maxLines = 1, color = MutedText, style = MaterialTheme.typography.bodySmall)
                    }
                    if (onPip != null) {
                        IconButton(onClick = { wakeTick++; onPip() }) {
                            Icon(painterResource(R.drawable.ic_pip), contentDescription = stringResource(R.string.tools_pip), tint = Color.White)
                        }
                    }
                    IconButton(onClick = { wakeTick++; onQueue() }) {
                        Icon(painterResource(R.drawable.ic_playlist), contentDescription = stringResource(R.string.player_playlist), tint = Color.White)
                    }
                    IconButton(onClick = { wakeTick++; onTools() }) {
                        Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.player_settings), tint = Color.White)
                    }
                }

                // Middle: jump back, previous, play or pause, next, jump forward
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (landscape) 36.dp else 18.dp)
                ) {
                    SkipButton("−10") {
                        engine.seekTo((positionNow - 10_000L).coerceAtLeast(0L))
                        wakeTick++
                    }
                    IconButton(onClick = { Playback.previous(); wakeTick++ }) {
                        Icon(painterResource(R.drawable.ic_previous), contentDescription = stringResource(R.string.player_previous), tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    PlayRing(playing, 76.dp) { engine.togglePlayPause(); wakeTick++ }
                    IconButton(onClick = { Playback.next(); wakeTick++ }) {
                        Icon(painterResource(R.drawable.ic_next), contentDescription = stringResource(R.string.player_next), tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    SkipButton("+10") {
                        val top = if (durationNow > 0L) durationNow else Long.MAX_VALUE
                        engine.seekTo((positionNow + 10_000L).coerceAtMost(top))
                        wakeTick++
                    }
                }

                // Bottom: seek bar and times, then lock, rotate, picture shape and speed
                Column(
                    Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .padding(horizontal = 20.dp).padding(top = 26.dp, bottom = 10.dp)
                ) {
                    PlayerSeek(engine, snap.positionMs, snap.durationMs) { wakeTick++ }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { locked = true; controlsVisible = false }) {
                            Icon(painterResource(R.drawable.ic_lock), contentDescription = stringResource(R.string.player_lock), tint = Color.White)
                        }
                        IconButton(onClick = {
                            wakeTick++
                            activity?.requestedOrientation = if (landscape) {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        }) {
                            Icon(painterResource(R.drawable.ic_rotate), contentDescription = stringResource(R.string.video_rotate), tint = Color.White)
                        }
                        Spacer(Modifier.width(4.dp))
                        PillButton(onClick = { fitIndex = (fitIndex + 1) % FitMode.values().size; wakeTick++ }) {
                            Icon(painterResource(R.drawable.ic_fullscreen), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                stringResource(
                                    when (fit) {
                                        FitMode.FIT -> R.string.video_fit
                                        FitMode.FILL -> R.string.video_fill
                                        FitMode.STRETCH -> R.string.video_stretch
                                    }
                                ),
                                style = MaterialTheme.typography.titleMedium,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        SpeedPill { wakeTick++ }
                    }
                }
            }
        }

        // Locked: only an unlock button, which appears when the screen is tapped
        AnimatedVisibility(
            visible = locked && controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.CenterStart).padding(start = 24.dp)
        ) {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(Color(0x99000000))
                    .border(2.dp, accent, CircleShape)
                    .clickable { locked = false; wakeTick++ },
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_lock), contentDescription = stringResource(R.string.player_unlock), tint = Color.White)
            }
        }

        if (hud.isNotEmpty()) {
            Box(
                Modifier.align(Alignment.TopCenter).padding(top = 72.dp).clip(RoundedCornerShape(20.dp))
                    .background(Color(0xCC000000)).padding(horizontal = 18.dp, vertical = 10.dp)
            ) {
                Text(hud, color = Color.White, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SkipButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(46.dp).clip(CircleShape).background(Color(0x66000000))
            .border(1.dp, Color(0x44FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}
