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
    var query by rememberSaveable { mutableStateOf("") }
    var hasAccess by remember { mutableStateOf(MediaSearch.hasAccess(ctx)) }
    var videos by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var music by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
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
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Box(Modifier.fillMaxWidth().height(if (landscape) 110.dp else 170.dp)) {
            BannerBackground(Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.BottomStart).padding(20.dp)) {
                Text(
                    stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    stringResource(R.string.home_tagline),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFFFFCDD2)
                )
            }
        }

        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.home_internet_title), style = MaterialTheme.typography.titleMedium)
            if (prefs.privateMode) {
                Text(
                    stringResource(R.string.private_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.home_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearchWeb(query.trim(), WebCategory.WEB) })
            )
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                items(WebCategory.entries) { category ->
                    AssistChip(
                        onClick = { onSearchWeb(query.trim(), category) },
                        label = { Text(stringResource(category.label)) }
                    )
                }
            }
            // Private Mode keeps recent searches hidden
            if (!prefs.privateMode && prefs.recent.isNotEmpty()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    items(prefs.recent) { term ->
                        SuggestionChip(
                            onClick = { onSearchWeb(term, WebCategory.WEB) },
                            label = { Text(term, maxLines = 1) }
                        )
                    }
                }
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 20.dp)
            ) {
                items(listOf(Dest.VIDEO, Dest.MUSIC, Dest.IPTV, Dest.SETTINGS)) { target ->
                    AssistChip(
                        onClick = { onOpen(target) },
                        label = { Text(stringResource(shortcutLabel(target))) }
                    )
                }
            }

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
                    MediaRow(stringResource(R.string.home_recent), recent) { index ->
                        if (Playback.playQueue(ctx, prefs, recent, index)) onPlayed()
                    }
                }
                MediaRow(stringResource(R.string.home_videos), videos) { index ->
                    if (Playback.playQueue(ctx, prefs, videos, index)) onPlayed()
                }
                MediaRow(stringResource(R.string.home_music), music) { index ->
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
private fun MediaRow(title: String, items: List<MediaItem>, onPlay: (Int) -> Unit) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
    )
    if (items.isEmpty()) {
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
            BannerBackground(Modifier.fillMaxSize())
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
