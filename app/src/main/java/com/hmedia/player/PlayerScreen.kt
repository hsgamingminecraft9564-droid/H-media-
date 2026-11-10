package com.hmedia.player

import android.view.SurfaceView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.hmedia.voidengine.VoidEngine
import com.hmedia.voidengine.togglePlayPause

// m:ss, or h:mm:ss for long files
private fun clock(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

// Music shows artwork and controls; video shows the picture. Both use the VOID engine's state.
@Composable
fun PlayerScreen(engine: VoidEngine, onBack: () -> Unit) {
    val snap by engine.snapshot.collectAsState()
    var dragValue by remember { mutableStateOf<Float?>(null) }
    val isAudio = Playback.kind == "audio"

    BackHandler { onBack() }

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(
            Playback.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            Playback.status,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        if (isAudio) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.size(220.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        stringResource(R.string.now_playing),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth().weight(1f),
                factory = { c -> SurfaceView(c).also { engine.attachVideoView(it) } }
            )
            DisposableEffect(engine) {
                onDispose { engine.detachVideoView() }
            }
        }

        val duration = snap.durationMs
        if (duration > 0) {
            val shown = dragValue ?: snap.positionMs.coerceIn(0L, duration).toFloat()
            Slider(
                value = shown.coerceIn(0f, duration.toFloat()),
                onValueChange = { dragValue = it },
                onValueChangeFinished = {
                    dragValue?.let { engine.seekTo(it.toLong()) }
                    dragValue = null
                },
                valueRange = 0f..duration.toFloat(),
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(clock(shown.toLong()), style = MaterialTheme.typography.bodySmall)
                Text(clock(duration), style = MaterialTheme.typography.bodySmall)
            }
        }

        Button(onClick = { engine.togglePlayPause() }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(if (snap.playWhenReady) stringResource(R.string.pause) else stringResource(R.string.play))
        }
    }
}
