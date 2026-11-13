package com.hmedia.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

// A number that goes up each time the screen returns to the foreground. Screens use it to re-check
// media access and reload their files, so permission granted in system settings, or a file added
// meanwhile, shows up without restarting the app.
@Composable
internal fun rememberResumeTick(): Int {
    val owner = LocalLifecycleOwner.current
    var tick by remember { mutableStateOf(0) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) tick++
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    return tick
}

// Empty artist or album names come back from the phone as "<unknown>" or blank
private fun artistName(item: MediaItem): String = item.artist.takeIf { it.isNotBlank() && it != "<unknown>" } ?: ""
private fun albumName(item: MediaItem): String = item.album.takeIf { it.isNotBlank() && it != "<unknown>" } ?: ""

// Video or music library built from the real files on the phone.
// Music has Songs, Artists and Albums tabs. Tapping a file plays the list in order, so Next and Previous work.
@Composable
fun LibraryScreen(prefs: AppPrefs, isVideo: Boolean, onPlayed: () -> Unit) {
    val ctx = LocalContext.current
    val resumeTick = rememberResumeTick()
    var hasAccess by remember { mutableStateOf(MediaSearch.hasAccess(ctx)) }
    var allItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(0) }            // music only: 0 songs, 1 artists, 2 albums
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        hasAccess = grants.values.any { it } || MediaSearch.hasAccess(ctx)
    }

    LaunchedEffect(resumeTick) {
        hasAccess = MediaSearch.hasAccess(ctx)
    }

    LaunchedEffect(hasAccess, isVideo, resumeTick) {
        allItems = if (hasAccess) {
            withContext(Dispatchers.IO) { if (isVideo) MediaSearch.list(ctx, true) else MusicIndex.load(ctx) }
        } else {
            emptyList()
        }
        loaded = true
    }

    val matching = allItems.filter { it.title.contains(filter, ignoreCase = true) }
    val groupKey = openGroup
    // What is listed and played: all songs, or the songs of one artist or album
    val shown: List<MediaItem> = when {
        isVideo || tab == 0 || groupKey == null -> matching
        tab == 1 -> matching.filter { artistName(it) == groupKey }
        else -> matching.filter { albumName(it) == groupKey }
    }
    val showingGroups = !isVideo && tab != 0 && groupKey == null

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            stringResource(if (isVideo) R.string.lib_title_video else R.string.lib_title_music),
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(Modifier.height(8.dp))
        if (!hasAccess) {
            Text(stringResource(R.string.lib_allow_help), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = { permLauncher.launch(MediaSearch.requiredPermissions()) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Text(stringResource(R.string.lib_allow))
            }
        } else {
            if (!isVideo) {
                TabRow(selectedTabIndex = tab) {
                    listOf(R.string.lib_tab_songs, R.string.lib_tab_artists, R.string.lib_tab_albums).forEachIndexed { index, label ->
                        Tab(
                            selected = tab == index,
                            onClick = {
                                tab = index
                                openGroup = null
                            },
                            text = { Text(stringResource(label)) }
                        )
                    }
                }
                if (groupKey != null && tab != 0) {
                    TextButton(onClick = { openGroup = null }) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.lib_back_to_list))
                    }
                }
                if (shown.isNotEmpty() && !showingGroups) {
                    Button(
                        onClick = {
                            message = ""
                            if (Playback.playQueueShuffled(ctx, prefs, shown)) onPlayed() else message = Playback.status
                        },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_shuffle), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.lib_shuffle_play))
                    }
                }
            }
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text(stringResource(R.string.lib_filter)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
            )
            if (message.isNotEmpty()) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            if (!loaded) {
                Text(
                    stringResource(R.string.lib_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else if (showingGroups) {
                val groups = matching
                    .groupBy { if (tab == 1) artistName(it) else albumName(it) }
                    .toList()
                    .sortedBy { it.first.lowercase() }
                if (groups.isEmpty()) {
                    Text(stringResource(R.string.lib_empty), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 16.dp))
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        items(groups) { group ->
                            Row(
                                Modifier.fillMaxWidth().clickable { openGroup = group.first }.padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    painterResource(if (tab == 1) R.drawable.ic_artist else R.drawable.ic_album),
                                    contentDescription = null,
                                    modifier = Modifier.size(28.dp)
                                )
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        group.first.ifBlank {
                                            stringResource(if (tab == 1) R.string.lib_unknown_artist else R.string.lib_unknown_album)
                                        },
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyLarge
                                    )
                                    Text(
                                        stringResource(R.string.lib_songs_count, group.second.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else if (shown.isEmpty()) {
                Text(
                    stringResource(R.string.lib_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(shown) { index, item ->
                        val playing = item.uri == Playback.currentUri
                        val artistText = artistName(item).ifBlank { stringResource(R.string.lib_unknown_artist) }
                        val albumText = albumName(item).ifBlank { stringResource(R.string.lib_unknown_album) }
                        Row(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    message = ""
                                    if (Playback.playQueue(ctx, prefs, shown, index)) {
                                        onPlayed()
                                    } else {
                                        message = Playback.status
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(width = 96.dp, height = 54.dp).clip(RoundedCornerShape(8.dp))) {
                                ThumbOrBanner(item.uri, Modifier.fillMaxSize())
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    item.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    if (isVideo) stringResource(R.string.video) else "$artistText | $albumText",
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
