package com.hmedia.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.hmedia.player.engine.VoidEngine
import kotlinx.coroutines.delay
import org.videolan.libvlc.util.VLCVideoLayout

// m:ss, or h:mm:ss for long files
private fun clock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// Shows the playing file. The music itself belongs to Playback, so leaving this screen never stops it.
@Composable
fun PlayerScreen(engine: VoidEngine, onBack: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    var playing by remember { mutableStateOf(engine.isPlayingNow()) }
    var position by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(0L) }
    var dragging by remember { mutableStateOf(false) }

    // Leaving the screen removes the picture. Video pauses (no picture to show); music keeps playing.
    DisposableEffect(engine) {
        onDispose {
            engine.detachVideo()
            if (engine.kind != "audio") engine.pause()
        }
    }

    // Video and streams pause when the app goes to the background. Music keeps playing.
    DisposableEffect(engine, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && engine.kind != "audio") engine.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Progress is refreshed twice a second, but not while the user drags the slider
    LaunchedEffect(engine) {
        while (true) {
            if (!dragging) position = engine.positionMs()
            duration = engine.durationMs()
            playing = engine.isPlayingNow()
            delay(500)
        }
    }

    BackHandler { onBack() }

    val status = Playback.status
    val engineLine = stringResource(R.string.engine_now, Playback.presetName) +
        if (status.isNotEmpty()) " · $status" else ""

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(
            Playback.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            engineLine,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (engine.kind == "audio") {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.now_playing),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp)
                )
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { c ->
                    VLCVideoLayout(c).also {
                        it.keepScreenOn = true
                        engine.attach(it)
                    }
                }
            )
        }

        if (duration > 0 && engine.kind != "stream") {
            Slider(
                value = position.coerceIn(0L, duration).toFloat(),
                onValueChange = { dragging = true; position = it.toLong() },
                onValueChangeFinished = { engine.seekTo(position); dragging = false },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(clock(position), style = MaterialTheme.typography.bodySmall)
                Text(clock(duration), style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(onClick = { engine.toggle() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(if (playing) stringResource(R.string.pause) else stringResource(R.string.play))
        }
    }
}
