package com.hmedia.player

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.hmedia.voidengine.VoidEngine
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val MiniHeight = 72.dp

// The player that shrinks into a mini bar, like YouTube or Spotify.
// Drag the handle or the mini bar down to shrink it, up to open it. Tap the mini bar to open it.
// When you let go it settles at the nearer end. Playback keeps going in both states.
@Composable
fun PlayerSheet(
    engine: VoidEngine,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    barHeight: Dp,
    miniContent: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(if (expanded) 1f else 0f) }
    LaunchedEffect(expanded) { progress.animateTo(if (expanded) 1f else 0f) }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val fullPx = with(density) { maxHeight.toPx() }
        val miniPx = with(density) { MiniHeight.toPx() }
        val barPx = with(density) { barHeight.toPx() }
        val p = progress.value.coerceIn(0f, 1f)
        val heightPx = miniPx + (fullPx - miniPx) * p
        // The mini bar sits just above the bottom bar; the full player covers everything
        val bottomPx = barPx * (1f - p)

        fun settle() {
            scope.launch {
                val target = if (progress.value > 0.5f) 1f else 0f
                progress.animateTo(target)
                onExpandedChange(target == 1f)
            }
        }

        val drag = Modifier.pointerInput(fullPx) {
            detectVerticalDragGestures(
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    val range = (fullPx - miniPx).coerceAtLeast(1f)
                    scope.launch { progress.snapTo((progress.value - dragAmount / range).coerceIn(0f, 1f)) }
                },
                onDragEnd = { settle() },
                onDragCancel = { settle() }
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .offset { IntOffset(0, (fullPx - heightPx - bottomPx).roundToInt()) }
                .height(with(density) { heightPx.toDp() })
                .background(Color(0xFF0B0B0F).copy(alpha = p))
        ) {
            if (p > 0.001f) {
                Box(Modifier.fillMaxSize().alpha(p)) {
                    PlayerScreen(engine) { onExpandedChange(false) }
                    // The handle at the top: drag it down to shrink the player
                    Box(
                        Modifier.align(Alignment.TopCenter).fillMaxWidth().height(24.dp).then(drag),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(Modifier.width(44.dp).height(4.dp).clip(RoundedCornerShape(2.dp)).background(Color(0x80FFFFFF)))
                    }
                }
            }
            if (p < 0.999f) {
                Box(Modifier.fillMaxWidth().height(MiniHeight).alpha(1f - p).then(drag)) {
                    miniContent()
                }
            }
        }
    }
}
