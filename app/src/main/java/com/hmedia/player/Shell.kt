package com.hmedia.player

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.shape.CircleShape

enum class Dest { HOME, VIDEO, MUSIC, IPTV, WEB, SETTINGS, PLAYER, SEARCH, PROFILE }

private const val SEARCH_HOME = "https://www.google.com/"
private const val SEARCH_QUERY = "https://www.google.com/search?q="

private val NAV_ENTRIES = listOf(
    Dest.HOME to R.string.nav_home,
    Dest.VIDEO to R.string.nav_video,
    Dest.MUSIC to R.string.nav_music,
    Dest.IPTV to R.string.nav_iptv,
    Dest.SETTINGS to R.string.nav_settings
)

// The app's main layout: welcome, the main screens, the now-playing strip and the navigation.
// Navigation is a bottom bar on a narrow portrait screen and a side rail on landscape or wide screens.
@Composable
fun AppShell(prefs: AppPrefs) {
    val ctx = LocalContext.current
    var started by rememberSaveable { mutableStateOf(prefs.consented) }
    var dest by rememberSaveable { mutableStateOf(Dest.HOME) }
    var returnTo by rememberSaveable { mutableStateOf(Dest.HOME) }
    var webUrl by rememberSaveable { mutableStateOf(SEARCH_HOME) }
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    // Asked once after the welcome screen, not again on every rotation
    LaunchedEffect(started) {
        if (started && !askedForNotifications && Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            askedForNotifications = true
            notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    // Back from Video, Music, IPTV or Settings returns to Home instead of leaving the app.
    // The web page and the player handle Back themselves.
    BackHandler(enabled = started && (dest == Dest.VIDEO || dest == Dest.MUSIC || dest == Dest.IPTV || dest == Dest.SETTINGS || dest == Dest.SEARCH || dest == Dest.PROFILE)) {
        dest = Dest.HOME
    }

    fun openPlayer(from: Dest) {
        returnTo = from
        dest = Dest.PLAYER
    }

    fun searchWeb(query: String, category: WebCategory) {
        val q = query.trim()
        if (q.isNotEmpty() && !prefs.privateMode) prefs.remember(q)
        webUrl = if (q.isEmpty()) SEARCH_HOME else SEARCH_QUERY + Uri.encode(q) + category.param
        dest = Dest.WEB
    }

    val config = LocalConfiguration.current
    val useRail = config.screenWidthDp >= 600 || config.orientation == Configuration.ORIENTATION_LANDSCAPE

    Surface(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        color = MaterialTheme.colorScheme.background
    ) {
        AppBackground(prefs)
        VideoBackdrop()
        if (!started) {
            WelcomeScreen(prefs) { started = true }
        } else {
            val showNav = dest != Dest.PLAYER
            Row(Modifier.fillMaxSize()) {
                if (showNav && useRail) {
                    SideRail(current = dest) { dest = it }
                }
                Column(Modifier.weight(1f).fillMaxHeight()) {
                    Box(Modifier.weight(1f).fillMaxWidth()) {
                        when (dest) {
                            Dest.HOME -> HomeDashboard(
                                prefs = prefs,
                                onOpen = { dest = it },
                                onSearchWeb = { q, c -> searchWeb(q, c) },
                                onPlayed = { openPlayer(Dest.HOME) }
                            )
                            Dest.VIDEO -> LibraryScreen(prefs, isVideo = true, onPlayed = { openPlayer(Dest.VIDEO) }, onSearch = { dest = Dest.SEARCH }, onProfile = { dest = Dest.PROFILE })
                            Dest.MUSIC -> LibraryScreen(prefs, isVideo = false, onPlayed = { openPlayer(Dest.MUSIC) }, onSearch = { dest = Dest.SEARCH }, onProfile = { dest = Dest.PROFILE })
                            Dest.IPTV -> IptvScreen(prefs, onPlayed = { openPlayer(Dest.IPTV) })
                            Dest.WEB -> WebSearchScreen(webUrl, prefs.privateMode, prefs.webScripts) {
                                dest = Dest.HOME
                            }
                            Dest.SETTINGS -> SettingsScreen(prefs, onProfile = { dest = Dest.PROFILE }) { dest = Dest.HOME }
                        Dest.SEARCH -> SearchHubScreen(prefs, onSearch = { q, c -> searchWeb(q, c) }) { dest = Dest.HOME }
                        Dest.PROFILE -> ProfileScreen(prefs) { dest = Dest.SETTINGS }
                            Dest.PLAYER -> {
                                val engine = Playback.engine
                                if (engine != null) {
                                    PlayerScreen(engine) { dest = returnTo }
                                } else {
                                    // Playback was stopped, for example from the notification
                                    LaunchedEffect(Unit) { dest = returnTo }
                                }
                            }
                        }
                    }
                    if (Playback.hasEngine && dest != Dest.PLAYER) {
                        NowPlayingStrip { openPlayer(dest) }
                    }
                    if (showNav && !useRail) {
                        BottomBar(current = dest) { dest = it }
                    }
                }
            }
        }
    }
}

@Composable
private fun NowPlayingStrip(onOpen: () -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    if (Playback.kind == "video") {
        // Video: film icon, title and status, a play or pause button and the three dots (as in the reference)
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                .clip(RoundedCornerShape(22.dp)).background(Color(0xB3101015))
                .clickable { onOpen() }
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF22222A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_video), contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    Playback.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                Text(
                    stringResource(if (Playback.playing) R.string.now_playing else R.string.paused),
                    style = MaterialTheme.typography.bodySmall,
                    color = MutedText
                )
            }
            IconButton(onClick = { Playback.toggle() }) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Color(0xFF1A1A20)), contentAlignment = Alignment.Center) {
                    Icon(
                        painterResource(if (Playback.playing) R.drawable.ic_pause else R.drawable.ic_play),
                        contentDescription = stringResource(if (Playback.playing) R.string.pause else R.string.play),
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Box(Modifier.size(width = 30.dp, height = 40.dp).clip(RoundedCornerShape(12.dp)).clickable { onOpen() }, contentAlignment = Alignment.Center) {
                MoreDots()
            }
        }
        return
    }
    // Music: the picture, title, status and the previous, play or pause and next buttons
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(22.dp)).background(Color(0xFF17171B))
            .clickable { onOpen() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF2A2A30))) {
            val uri = Playback.currentUri
            if (uri != null) ThumbOrBanner(uri, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(Playback.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall, color = Color.White)
            Text(
                stringResource(if (Playback.playing) R.string.now_playing else R.string.paused),
                style = MaterialTheme.typography.bodySmall,
                color = MutedText
            )
        }
        IconButton(onClick = { Playback.previous() }) {
            Icon(painterResource(R.drawable.ic_previous), contentDescription = stringResource(R.string.player_previous), tint = Color.White, modifier = Modifier.size(22.dp))
        }
        IconButton(onClick = { Playback.toggle() }) {
            Icon(
                painterResource(if (Playback.playing) R.drawable.ic_pause else R.drawable.ic_play),
                contentDescription = stringResource(if (Playback.playing) R.string.pause else R.string.play),
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
        IconButton(onClick = { Playback.next() }) {
            Icon(painterResource(R.drawable.ic_next), contentDescription = stringResource(R.string.player_next), tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

// Bottom bar as in the reference: a wide dark bar, thin dividers, the selected item with a red glow
@Composable
private fun BottomBar(current: Dest, onSelect: (Dest) -> Unit) {
    val entries = listOf(
        Dest.HOME to R.string.nav_home,
        Dest.VIDEO to R.string.nav_video,
        Dest.MUSIC to R.string.nav_music,
        Dest.IPTV to R.string.nav_iptv,
        Dest.SETTINGS to R.string.nav_settings
    )
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(32.dp))
            .background(Color(0xE60D0D11))
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        entries.forEachIndexed { index, (d, label) ->
            val selected = current == d
            if (index > 0) {
                Box(Modifier.width(1.dp).height(34.dp).background(Color(0x22FFFFFF)))
            }
            Column(
                Modifier.weight(1f)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        Brush.verticalGradient(
                            if (selected) listOf(accent.copy(alpha = 0.30f), Color.Transparent)
                            else listOf(Color.Transparent, Color.Transparent)
                        )
                    )
                    .clickable { onSelect(d) }
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    painterResource(navIcon(d)),
                    contentDescription = null,
                    tint = if (selected) accent else Color(0xFFE6E6EA),
                    modifier = Modifier.size(24.dp)
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(label),
                    maxLines = 1,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) accent else Color(0xFFE6E6EA)
                )
            }
        }
    }
}

private fun navIcon(dest: Dest): Int = when (dest) {
    Dest.HOME -> R.drawable.ic_home
    Dest.VIDEO -> R.drawable.ic_video
    Dest.MUSIC -> R.drawable.ic_music
    Dest.IPTV -> R.drawable.ic_network
    else -> R.drawable.ic_settings
}
