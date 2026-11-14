package com.hmedia.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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

// Empty artist or album names come back as "<unknown>" or blank
private fun artistName(item: MediaItem): String = item.artist.takeIf { it.isNotBlank() && it != "<unknown>" } ?: ""
private fun albumName(item: MediaItem): String = item.album.takeIf { it.isNotBlank() && it != "<unknown>" } ?: ""

// Music and video library: header, title, tabs, shuffle, filter, then the list.
// Tapping a file plays the filtered list in order, so Next and Previous work.
@Composable
fun LibraryScreen(
    prefs: AppPrefs,
    isVideo: Boolean,
    onPlayed: () -> Unit,
    onSearch: (() -> Unit)? = null,
    onProfile: (() -> Unit)? = null
) {
    val ctx = LocalContext.current
    val resumeTick = rememberResumeTick()
    var hasAccess by remember { mutableStateOf(MediaSearch.hasAccess(ctx)) }
    var allItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    var actionsFor by remember { mutableStateOf<MediaItem?>(null) }
    var reload by remember { mutableStateOf(0) }
    var tab by rememberSaveable { mutableStateOf(0) }            // music only: 0 songs, 1 artists, 2 albums
    var openGroup by rememberSaveable { mutableStateOf<String?>(null) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        hasAccess = grants.values.any { it } || MediaSearch.hasAccess(ctx)
    }

    LaunchedEffect(resumeTick) {
        hasAccess = MediaSearch.hasAccess(ctx)
    }

    LaunchedEffect(hasAccess, isVideo, resumeTick, reload) {
        allItems = if (hasAccess) {
            withContext(Dispatchers.IO) { if (isVideo) VideoIndex.load(ctx) else MusicIndex.load(ctx) }
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

    fun playAt(index: Int) {
        message = ""
        if (Playback.playQueue(ctx, prefs, shown, index)) onPlayed() else message = Playback.status
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        AppHeader(prefs, onSearch, onProfile)
        ScreenTitle(stringResource(if (isVideo) R.string.lib_title_video else R.string.lib_title_music))

        if (!hasAccess) {
            Text(stringResource(R.string.lib_allow_help), style = MaterialTheme.typography.bodyMedium, color = MutedText)
            Spacer(Modifier.height(12.dp))
            GradientButton(stringResource(R.string.lib_allow), null) {
                permLauncher.launch(MediaSearch.requiredPermissions())
            }
        } else {
            if (!isVideo) {
                PillTabs(
                    labels = listOf(R.string.lib_tab_songs, R.string.lib_tab_artists, R.string.lib_tab_albums),
                    icons = listOf(R.drawable.ic_music, R.drawable.ic_artist, R.drawable.ic_album),
                    selected = tab,
                    onSelect = {
                        tab = it
                        openGroup = null
                    }
                )
                Spacer(Modifier.height(12.dp))
                if (groupKey != null && tab != 0) {
                    TextButton(onClick = { openGroup = null }) {
                        Icon(painterResource(R.drawable.ic_back), contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.lib_back_to_list), color = MaterialTheme.colorScheme.primary)
                    }
                }
                if (shown.isNotEmpty() && !showingGroups) {
                    GradientButton(stringResource(R.string.lib_shuffle_play), R.drawable.ic_shuffle) {
                        message = ""
                        if (Playback.playQueueShuffled(ctx, prefs, shown)) onPlayed() else message = Playback.status
                    }
                    Spacer(Modifier.height(12.dp))
                }
            }

            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text(stringResource(R.string.lib_filter), color = MutedText) },
                singleLine = true,
                leadingIcon = { Icon(painterResource(R.drawable.ic_search), contentDescription = null, tint = MutedText) },
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth()
            )
            if (message.isNotEmpty()) {
                Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Spacer(Modifier.height(4.dp))

            when {
                !loaded -> Text(
                    stringResource(R.string.lib_loading),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                    modifier = Modifier.padding(top = 16.dp)
                )
                showingGroups -> {
                    val groups = matching
                        .groupBy { if (tab == 1) artistName(it) else albumName(it) }
                        .toList()
                        .sortedBy { it.first.lowercase() }
                    if (groups.isEmpty()) {
                        Text(stringResource(R.string.lib_empty), style = MaterialTheme.typography.bodyMedium, color = MutedText, modifier = Modifier.padding(top = 16.dp))
                    } else {
                        LazyColumn(Modifier.weight(1f)) {
                            items(groups) { group ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { openGroup = group.first }.padding(vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        Modifier.size(60.dp).clip(CircleShape).background(Color(0xFF16161A)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painterResource(if (tab == 1) R.drawable.ic_artist else R.drawable.ic_album),
                                            contentDescription = null,
                                            tint = MutedText,
                                            modifier = Modifier.size(26.dp)
                                        )
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    Column(Modifier.weight(1f)) {
                                        Text(
                                            group.first.ifBlank {
                                                stringResource(if (tab == 1) R.string.lib_unknown_artist else R.string.lib_unknown_album)
                                            },
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = Color.White
                                        )
                                        Text(
                                            stringResource(R.string.lib_songs_count, group.second.size),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MutedText
                                        )
                                    }
                                    MoreDots()
                                }
                            }
                        }
                    }
                }
                shown.isEmpty() -> Text(
                    stringResource(R.string.lib_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MutedText,
                    modifier = Modifier.padding(top = 16.dp)
                )
                else -> LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(shown) { index, item ->
                        if (isVideo) {
                            VideoRow(item, onMore = { actionsFor = item }) { playAt(index) }
                        } else {
                            SongRow(item, onMore = { actionsFor = item }) { playAt(index) }
                        }
                    }
                }
            }
        }
    }

    actionsFor?.let { item ->
        FileActionsSheet(
            target = FileTarget(item.uri, item.title, if (item.isVideo) "video/*" else "audio/*"),
            onDismiss = { actionsFor = null },
            onChanged = { reload++ }
        )
    }
}

// One song: square picture, title, artist, length and the three dots
@Composable
private fun SongRow(item: MediaItem, onMore: () -> Unit, onClick: () -> Unit) {
    val playing = item.uri == Playback.currentUri
    val artist = artistName(item).ifBlank { stringResource(R.string.lib_unknown_artist) }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))) {
            ThumbOrBanner(item.uri, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleMedium,
                color = if (playing) MaterialTheme.colorScheme.primary else Color.White
            )
            Text(
                artist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }
        if (item.durationMs > 0) {
            Text(durationLabel(item.durationMs), style = MaterialTheme.typography.bodySmall, color = MutedText)
            Spacer(Modifier.width(8.dp))
        }
        Box(
            Modifier.size(width = 40.dp, height = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onMore),
            contentAlignment = Alignment.Center
        ) { MoreDots() }
    }
}

// One video: wide picture with the length on it, title, the word Video and the file size, the three dots
@Composable
private fun VideoRow(item: MediaItem, onMore: () -> Unit, onClick: () -> Unit) {
    val playing = item.uri == Playback.currentUri
    val sizeText = sizeLabel(item.sizeBytes)
    val subtitle = if (sizeText.isEmpty()) stringResource(R.string.video) else stringResource(R.string.video) + " • " + sizeText
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(128.dp).height(72.dp).clip(RoundedCornerShape(12.dp))) {
            ThumbOrBanner(item.uri, Modifier.fillMaxSize())
            if (item.durationMs > 0) {
                Text(
                    durationLabel(item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)
                        .background(Color(0xB3000000), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
                color = if (playing) MaterialTheme.colorScheme.primary else Color.White
            )
            Text(subtitle, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MutedText)
        }
        Box(
            Modifier.size(width = 40.dp, height = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onMore),
            contentAlignment = Alignment.Center
        ) { MoreDots() }
    }
}
