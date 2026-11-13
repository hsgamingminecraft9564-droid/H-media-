package com.hmedia.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.foundation.background
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image

// Internet search categories. The parameter is added to the search address.
enum class WebCategory(val label: Int, val param: String) {
    WEB(R.string.web_all, ""),
    IMAGES(R.string.web_images, "&iax=images&ia=images"),
    VIDEOS(R.string.web_videos, "&iax=videos&ia=videos"),
    NEWS(R.string.web_news, "&iar=news&ia=news")
}

// A red-moon banner drawn in code, so it needs no image file.
@Composable
fun BannerBackground(modifier: Modifier) {
    Canvas(modifier = modifier) {
        drawRect(brush = Brush.verticalGradient(listOf(Color(0xFF3A0508), Color(0xFF0B0B0C))))
        val radius = size.minDimension * 0.42f
        val center = Offset(size.width * 0.82f, size.height * 0.30f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFE53935), Color(0xFF7F0A10), Color(0x00000000)),
                center = center,
                radius = radius
            ),
            radius = radius,
            center = center
        )
    }
}

// Home: the internet search bar, and the media files of the phone on banner cards.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeDashboard(
    prefs: AppPrefs,
    onOpen: (Dest) -> Unit,
    onSearchWeb: (String, WebCategory) -> Unit,
    onPlayed: () -> Unit
) {
    val ctx = LocalContext.current
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val resumeTick = rememberResumeTick()
    var hasAccess by remember { mutableStateOf(MediaSearch.hasAccess(ctx)) }
    var videos by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var music by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    // False until the first load finishes, so "No files" is not shown while the lists are still loading
    var loaded by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        hasAccess = grants.values.any { it } || MediaSearch.hasAccess(ctx)
    }

    LaunchedEffect(resumeTick) {
        hasAccess = MediaSearch.hasAccess(ctx)
    }

    LaunchedEffect(hasAccess, resumeTick) {
        if (hasAccess) {
            videos = withContext(Dispatchers.IO) { MediaSearch.list(ctx, true) }
            music = withContext(Dispatchers.IO) { MediaSearch.list(ctx, false) }
        } else {
            videos = emptyList()
            music = emptyList()
        }
        loaded = true
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(if (landscape) 96.dp else 132.dp)) {
            BannerBackground(Modifier.fillMaxSize())
            Row(Modifier.align(Alignment.TopEnd).padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onOpen(Dest.SEARCH) }) {
                    Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.search_title), tint = Color.White)
                }
                ProfileAvatar(prefs, 40.dp) { onOpen(Dest.PROFILE) }
            }
             Row(Modifier.align(Alignment.TopStart).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Image(painterResource(R.drawable.hmp_logo), contentDescription = null, modifier = Modifier.size(36.dp))
                Spacer(Modifier.width(10.dp))
                Text(
                    stringResource(R.string.home_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }        }

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            if (!hasAccess) {
                Card(Modifier.fillMaxWidth().padding(top = 20.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text(stringResource(R.string.lib_allow_help), style = MaterialTheme.typography.bodyMedium)
                        Button(
                            onClick = { permLauncher.launch(MediaSearch.requiredPermissions()) },
                            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
                        ) {
                            Text(stringResource(R.string.lib_allow))
                        }
                    }
                }
            } else {
                val recent = Playback.recent
                if (recent.isNotEmpty()) {
                    MediaRow(stringResource(R.string.home_recent), recent, loaded = true) { index ->
                        if (Playback.playQueue(ctx, prefs, recent, index)) onPlayed()
                    }
                }
                MediaRow(stringResource(R.string.home_videos), videos, loaded) { index ->
                    if (Playback.playQueue(ctx, prefs, videos, index)) onPlayed()
                }
                MediaRow(stringResource(R.string.home_music), music, loaded) { index ->
                    if (Playback.playQueue(ctx, prefs, music, index)) onPlayed()
                }
            }
        }
    }
}

private fun shortcutLabel(target: Dest): Int = when (target) {
    Dest.VIDEO -> R.string.home_video
    Dest.MUSIC -> R.string.home_music
    Dest.IPTV -> R.string.home_iptv
    else -> R.string.home_settings
}

@Composable
private fun MediaRow(title: String, items: List<MediaItem>, loaded: Boolean, onPlay: (Int) -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
    if (!loaded) {
        Text(stringResource(R.string.lib_loading), style = MaterialTheme.typography.bodySmall)
    } else if (items.isEmpty()) {
        Text(stringResource(R.string.lib_empty), style = MaterialTheme.typography.bodySmall)
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(items) { index, item ->
                MediaCard(item) { onPlay(index) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MediaCard(item: MediaItem, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.width(160.dp).height(110.dp)) {
        Box(Modifier.fillMaxSize()) {
            ThumbOrBanner(item.uri, Modifier.fillMaxSize())
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0x00000000), Color(0xCC000000)))
                )
            )
            Column(Modifier.align(Alignment.BottomStart).padding(10.dp)) {
                Text(
                    item.title,
                    maxLines = 2,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                Text(
                    stringResource(if (item.isVideo) R.string.video else R.string.audio),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFFFCDD2)
                )
            }
        }
    }
}

private fun shortcutIcon(target: Dest): Int = when (target) {
    Dest.VIDEO -> R.drawable.ic_video
    Dest.MUSIC -> R.drawable.ic_music
    Dest.IPTV -> R.drawable.ic_network
    else -> R.drawable.ic_settings
}
