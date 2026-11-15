/*
 * Copyright (c) 2026 Himanshu. All rights reserved.
 */

package com.hmedia.player

import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmedia.voidengine.PlayerSnapshot
import com.hmedia.voidengine.VoidEngine
import com.hmedia.voidengine.features.equalizer.EqualizerPresets
import kotlinx.coroutines.delay

private val ToolFill = Color(0xCC101014)
private val ToolBorder = Color(0x26FFFFFF)
private val SLEEP_CHOICES = listOf(0, 15, 30, 45, 60, -1)

// Everything the player can do beyond play and pause, in one sheet: shuffle and repeat, speed from
// 0.25x to 4x, audio and subtitle tracks, equalizer, sleep timer, A-B repeat, bookmarks, favourite,
// Picture-in-Picture and a small info panel. Each control is hidden when the file or engine cannot do it.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerToolsSheet(
    engine: VoidEngine,
    snap: PlayerSnapshot,
    isAudio: Boolean,
    onPip: (() -> Unit)?,
    onDismiss: () -> Unit
) {
    val prefs = Playback.prefs
    val uri = Playback.currentUri
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF111115),
        contentColor = Color.White
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp).padding(bottom = 32.dp)
        ) {
            Text(stringResource(R.string.tools_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(16.dp))

            // Shuffle and repeat
            ToolLabel(stringResource(R.string.player_shuffle))
            ChipRows(
                listOf(
                    stringResource(R.string.player_shuffle_off) to !Playback.shuffle,
                    stringResource(R.string.player_shuffle_on) to Playback.shuffle
                ),
                2
            ) { index -> if ((index == 1) != Playback.shuffle) Playback.toggleShuffle() }
            ToolLabel(stringResource(R.string.player_repeat))
            ChipRows(
                RepeatMode.values().map { mode -> stringResource(repeatLabel(mode)) to (Playback.repeat == mode) },
                3
            ) { index -> Playback.applyRepeat(RepeatMode.values()[index]) }

            // Speed, 0.25x to 4x
            ToolLabel(stringResource(R.string.player_speed))
            ChipRows(SPEEDS.map { speedLabel(it) to (Playback.playbackSpeed == it) }, 5) { index ->
                Playback.setSpeed(SPEEDS[index])
            }

            // Audio track
            if (snap.audioTracks.size > 1) {
                ToolLabel(stringResource(R.string.tools_audio_track))
                snap.audioTracks.forEach { track ->
                    val name = if (track.language.isNullOrBlank()) track.label else track.label + " (" + track.language + ")"
                    PickRow(name, snap.selectedAudioId == track.id) { engine.selectAudioTrack(track.id) }
                }
            }

            // Subtitles
            if (!isAudio) {
                ToolLabel(stringResource(R.string.tools_subtitles))
                if (snap.subtitleTracks.isEmpty()) {
                    Text(stringResource(R.string.tools_subtitles_none), style = MaterialTheme.typography.bodySmall, color = MutedText)
                } else {
                    PickRow(stringResource(R.string.tools_subtitles_off), snap.selectedSubtitleId == null) {
                        engine.selectSubtitleTrack(null)
                    }
                    snap.subtitleTracks.forEach { track ->
                        val name = if (track.language.isNullOrBlank()) track.label else track.label + " (" + track.language + ")"
                        PickRow(name, snap.selectedSubtitleId == track.id) { engine.selectSubtitleTrack(track.id) }
                    }
                }
            }

            // Equalizer
            ToolLabel(stringResource(R.string.tools_equalizer))
            if (snap.equalizerBandsHz.isEmpty()) {
                Text(stringResource(R.string.tools_equalizer_none), style = MaterialTheme.typography.bodySmall, color = MutedText)
            } else {
                ChipRows(EqualizerPresets.names.map { it to (prefs?.eqPreset == it) }, 2) { index ->
                    Playback.chooseEqualizer(EqualizerPresets.names[index])
                }
            }

            // Sleep timer
            ToolLabel(stringResource(R.string.tools_sleep))
            val sleepEnds = Playback.sleepEndsAtMs
            var sleepNow by remember { mutableStateOf(SystemClock.elapsedRealtime()) }
            LaunchedEffect(sleepEnds) {
                while (sleepEnds > 0L) {
                    sleepNow = SystemClock.elapsedRealtime()
                    delay(1000L)
                }
            }
            ChipRows(
                SLEEP_CHOICES.map { choice ->
                    when (choice) {
                        0 -> stringResource(R.string.tools_sleep_off) to (sleepEnds == 0L && !Playback.sleepAfterTrack)
                        -1 -> stringResource(R.string.tools_sleep_track) to Playback.sleepAfterTrack
                        else -> stringResource(R.string.tools_sleep_minutes, choice) to false
                    }
                },
                3
            ) { index ->
                when (val choice = SLEEP_CHOICES[index]) {
                    0 -> Playback.cancelSleep()
                    -1 -> Playback.sleepAfterCurrentTrack()
                    else -> Playback.startSleepTimer(choice)
                }
            }
            if (sleepEnds > 0L) {
                Text(
                    stringResource(R.string.tools_sleep_left, clock((sleepEnds - sleepNow).coerceAtLeast(0L))),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (Playback.sleepAfterTrack) {
                Text(
                    stringResource(R.string.tools_sleep_track_on),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // A-B repeat
            ToolLabel(stringResource(R.string.tools_ab))
            val a = Playback.abStartMs
            val b = Playback.abEndMs
            Text(
                if (a < 0L && b < 0L) stringResource(R.string.tools_ab_none)
                else stringResource(R.string.tools_ab_state, if (a >= 0L) clock(a) else "–", if (b >= 0L) clock(b) else "–"),
                style = MaterialTheme.typography.bodySmall,
                color = MutedText,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ToolChip(stringResource(R.string.tools_ab_set_a), false, Modifier.weight(1f)) { Playback.markAbStart() }
                ToolChip(stringResource(R.string.tools_ab_set_b), false, Modifier.weight(1f)) { Playback.markAbEnd() }
                ToolChip(stringResource(R.string.tools_ab_clear), false, Modifier.weight(1f)) { Playback.clearAb() }
            }

            // Bookmarks
            ToolLabel(stringResource(R.string.tools_bookmarks))
            if (prefs != null && uri != null) {
                ToolChip(stringResource(R.string.tools_bookmark_add, clock(snap.positionMs)), false, Modifier.fillMaxWidth()) {
                    prefs.addBookmark(uri, snap.positionMs)
                }
                val marks = prefs.bookmarks(uri)
                if (marks.isEmpty()) {
                    Text(
                        stringResource(R.string.tools_bookmark_none),
                        style = MaterialTheme.typography.bodySmall,
                        color = MutedText,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                marks.forEach { ms ->
                    Row(
                        Modifier.fillMaxWidth().clickable { engine.seekTo(ms) }.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(painterResource(R.drawable.ic_history), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(14.dp))
                        Text(clock(ms), modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                        IconButton(onClick = { prefs.removeBookmark(uri, ms) }) {
                            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.file_delete), tint = Color.White)
                        }
                    }
                }
            }

            // Favourite and Picture-in-Picture
            Spacer(Modifier.height(12.dp))
            if (prefs != null && uri != null) {
                val favorite = prefs.isMediaFavorite(uri)
                Row(
                    Modifier.fillMaxWidth().clickable {
                        prefs.toggleMediaFavorite(MediaItem(title = Playback.title, uri = uri, isVideo = !isAudio))
                    }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        painterResource(R.drawable.ic_favorite),
                        contentDescription = null,
                        tint = if (favorite) MaterialTheme.colorScheme.primary else Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(18.dp))
                    Text(
                        stringResource(if (favorite) R.string.tools_favorite_remove else R.string.tools_favorite_add),
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
            if (onPip != null && !isAudio) {
                Row(
                    Modifier.fillMaxWidth().clickable { onDismiss(); onPip() }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(painterResource(R.drawable.ic_pip), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(18.dp))
                    Text(stringResource(R.string.tools_pip), style = MaterialTheme.typography.titleMedium)
                }
            }

            // Info
            ToolLabel(stringResource(R.string.tools_info))
            InfoLine(stringResource(R.string.tools_info_engine), snap.backendName ?: "–")
            InfoLine(stringResource(R.string.tools_info_state), snap.state.name)
            if (snap.videoWidth > 0 && snap.videoHeight > 0) {
                InfoLine(stringResource(R.string.tools_info_picture), "${snap.videoWidth}×${snap.videoHeight}")
            }
            if (snap.durationMs > 0L) InfoLine(stringResource(R.string.tools_info_length), clock(snap.durationMs))
            InfoLine(stringResource(R.string.player_speed), speedLabel(Playback.playbackSpeed))
            InfoLine(stringResource(R.string.tools_info_audio), snap.audioTracks.size.toString())
            InfoLine(stringResource(R.string.tools_info_subtitles), snap.subtitleTracks.size.toString())
        }
    }
}

@Composable
private fun ToolLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 18.dp, bottom = 8.dp)
    )
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MutedText, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun PickRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
    }
}

// Choices in rows of [perRow], filled in order; the last row is padded so the buttons keep their width
@Composable
private fun ChipRows(items: List<Pair<String, Boolean>>, perRow: Int, onPick: (Int) -> Unit) {
    items.withIndex().toList().chunked(perRow).forEach { row ->
        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            row.forEach { entry ->
                ToolChip(entry.value.first, entry.value.second, Modifier.weight(1f)) { onPick(entry.index) }
            }
            for (i in 0 until perRow - row.size) Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun ToolChip(label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        modifier.height(44.dp).clip(shape)
            .background(if (selected) MaterialTheme.colorScheme.primary else ToolFill)
            .border(1.dp, if (selected) Color.Transparent else ToolBorder, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 6.dp)
        )
    }
}
