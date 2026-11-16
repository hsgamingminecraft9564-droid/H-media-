package com.hmedia.player

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// My notes: private notes and timestamp bookmarks. Tap one to open its file at the saved time.
@Composable
fun NotesScreen(prefs: AppPrefs, onPlayed: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var entries by remember { mutableStateOf(MediaNotes.all(ctx)) }
    var filter by rememberSaveable { mutableStateOf("") }
    var draft by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val nowUri = Playback.currentUri
    val shown = entries.filter {
        it.text.contains(filter, ignoreCase = true) || it.mediaTitle.contains(filter, ignoreCase = true)
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
            }
        }
        ScreenTitle(stringResource(R.string.notes_title))

        // A note about the file that is playing now
        OutlinedTextField(
            value = draft,
            onValueChange = { draft = it.take(500) },
            placeholder = { Text(stringResource(R.string.notes_hint), color = MutedText) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            maxLines = 3
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(
                enabled = nowUri != null && draft.isNotBlank(),
                onClick = {
                    val uri = nowUri ?: return@TextButton
                    MediaNotes.add(ctx, uri.toString(), Playback.title, -1L, draft)
                    draft = ""
                    entries = MediaNotes.all(ctx)
                }
            ) { Text(stringResource(R.string.notes_add)) }
        }
        if (nowUri == null) {
            Text(stringResource(R.string.notes_nothing_playing), style = MaterialTheme.typography.bodySmall, color = MutedText)
        }

        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text(stringResource(R.string.notes_filter), color = MutedText) },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier.fillMaxWidth()
        )
        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }

        if (shown.isEmpty()) {
            Text(
                stringResource(R.string.notes_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(shown, key = { it.id }) { entry ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable {
                                message = ""
                                val started = Playback.play(ctx, prefs, Uri.parse(entry.mediaKey), entry.mediaTitle)
                                if (started) {
                                    if (entry.positionMs >= 0) Playback.seekTo(entry.positionMs)
                                    onPlayed()
                                } else {
                                    message = Playback.status.ifBlank { ctx.getString(R.string.notes_missing) }
                                }
                            }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(
                            entry.mediaTitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (entry.positionMs >= 0) {
                            Text(
                                stringResource(R.string.notes_at, durationLabel(entry.positionMs)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        if (entry.text.isNotEmpty()) {
                            Text(entry.text, style = MaterialTheme.typography.bodyMedium, color = MutedText)
                        }
                        TextButton(onClick = {
                            MediaNotes.delete(ctx, entry.id)
                            entries = MediaNotes.all(ctx)
                        }) { Text(stringResource(R.string.notes_delete)) }
                    }
                }
            }
        }
    }
}
