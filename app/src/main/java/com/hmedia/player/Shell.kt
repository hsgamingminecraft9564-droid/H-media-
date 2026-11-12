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

enum class Dest { HOME, VIDEO, MUSIC, IPTV, WEB, SETTINGS, PLAYER }

private const val SEARCH_HOME = "https://duckduckgo.com/"
private const val SEARCH_QUERY = "https://duckduckgo.com/?q="

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
    BackHandler(enabled = started && (dest == Dest.VIDEO || dest == Dest.MUSIC || dest == Dest.IPTV || dest == Dest.SETTINGS)) {
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
                            Dest.VIDEO -> LibraryScreen(prefs, isVideo = true, onPlayed = { openPlayer(Dest.VIDEO) })
                            Dest.MUSIC -> LibraryScreen(prefs, isVideo = false, onPlayed = { openPlayer(Dest.MUSIC) })
                            Dest.IPTV -> IptvScreen(prefs, onPlayed = { openPlayer(Dest.IPTV) })
                            Dest.WEB -> WebSearchScreen(webUrl, prefs.privateMode, prefs.webScripts) {
                                dest = Dest.HOME
                            }
                            Dest.SETTINGS -> SettingsScreen(prefs) { dest = Dest.HOME }
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
    Row(
        Modifier.fillMaxWidth().clickable { onOpen() }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(Playback.title, maxLines = 1, style = MaterialTheme.typography.bodyMedium)
            Text(
                stringResource(if (Playback.playing) R.string.now_playing else R.string.paused),
                style = MaterialTheme.typography.bodySmall
            )
        }
        TextButton(onClick = { Playback.toggle() }) {
            Text(stringResource(if (Playback.playing) R.string.pause else R.string.play))
        }
    }
}

@Composable
private fun BottomBar(current: Dest, onSelect: (Dest) -> Unit) {
    NavigationBar {
        NAV_ENTRIES.forEach { (d, label) ->
            NavigationBarItem(
                selected = current == d,
                onClick = { onSelect(d) },
                icon = { Icon(painterResource(navIcon(d)), contentDescription = null) },
                label = { Text(stringResource(label)) }
            )
        }
    }
}

// Side navigation. It scrolls, so every item stays reachable on a short landscape screen.
@Composable
private fun SideRail(current: Dest, onSelect: (Dest) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(
            Modifier.fillMaxHeight().width(88.dp).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NAV_ENTRIES.forEach { (d, label) ->
                NavigationRailItem(
                    selected = current == d,
                    onClick = { onSelect(d) },
                    icon = { Icon(painterResource(navIcon(d)), contentDescription = null) },
                    label = { Text(stringResource(label), maxLines = 1) }
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
