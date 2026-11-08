package com.hmedia.player

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.net.http.SslError
import android.provider.Settings
import android.widget.Toast
import android.webkit.SslErrorHandler
import androidx.core.app.ActivityCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import com.hmedia.player.web.WebPolicy
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.Image
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.hmedia.player.engine.VoidPresets

// Holds the WebView without triggering Compose state writes while the view is being created
private class WebHolder {
    var view: WebView? = null
}

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val prefs = remember { AppPrefs(applicationContext) }
            HMediaTheme(prefs.theme, prefs.font) {
                HMediaApp(prefs)
            }
        }
    }
}

enum class Screen { WELCOME, HOME, RESULTS, WEB, SETTINGS, PLAYER }

@Composable
fun HMediaApp(prefs: AppPrefs) {
    var screen by rememberSaveable { mutableStateOf(if (prefs.consented) Screen.HOME else Screen.WELCOME) }
    var webQuery by rememberSaveable { mutableStateOf("") }
    var webUrl by rememberSaveable { mutableStateOf("") }

    // If playback is stopped from the notification while the player is open, go back home
    val hasEngine = Playback.hasEngine
    LaunchedEffect(hasEngine) {
        if (!hasEngine && screen == Screen.PLAYER) screen = Screen.HOME
    }

    val ctx = LocalContext.current
    // Notification permission is asked the first time music plays (Android 13 and newer)
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Surface(
        modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
        color = MaterialTheme.colorScheme.background
    ) {
        when (screen) {
            Screen.WELCOME -> WelcomeScreen(prefs) { screen = Screen.HOME }
            Screen.HOME -> HomeScreen(
                prefs = prefs,
                onSettings = { screen = Screen.SETTINGS },
                onWeb = { q -> prefs.remember(q); webQuery = q; screen = Screen.RESULTS },
                onPlay = { item ->
                    if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    if (Playback.play(ctx, prefs, item.uri, item.title)) {
                        screen = Screen.PLAYER
                    } else {
                        Toast.makeText(ctx, Playback.status, Toast.LENGTH_LONG).show()
                    }
                },
                onOpenPlayer = { screen = Screen.PLAYER }
            )
            Screen.RESULTS -> SearchResultsScreen(
                query = webQuery,
                onOpen = { u -> webUrl = u; screen = Screen.WEB },
                onBack = { screen = Screen.HOME }
            )
            Screen.WEB -> WebSearchScreen(webUrl, prefs.privateMode, prefs.webScripts) { screen = Screen.RESULTS }
            Screen.SETTINGS -> SettingsScreen(prefs) { screen = Screen.HOME }
            Screen.PLAYER -> Playback.engine?.let { engine -> PlayerScreen(engine) { screen = Screen.HOME } }
        }
    }
}

@Composable
fun WelcomeScreen(prefs: AppPrefs, onStart: () -> Unit) {
    var showPolicy by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0xFF060606))) {
    Image(
        painter = painterResource(R.drawable.welcome_bg),
        contentDescription = null,
        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
        contentScale = ContentScale.FillWidth
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("H", style = MaterialTheme.typography.displayLarge, color = MaterialTheme.colorScheme.primary)
        Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.welcome_tagline), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(32.dp))
        Text(stringResource(R.string.welcome), style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(24.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = prefs.consented, onCheckedChange = prefs::setConsent)
            Text(
                stringResource(R.string.consent_text),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f)
            )
        }
        TextButton(onClick = { showPolicy = true }) { Text(stringResource(R.string.privacy_read)) }
        if (showPolicy) {
            AlertDialog(
                onDismissRequest = { showPolicy = false },
                confirmButton = { TextButton(onClick = { showPolicy = false }) { Text(stringResource(R.string.close)) } },
                title = { Text(stringResource(R.string.privacy_title)) },
                text = { Text(stringResource(R.string.privacy_text)) }
            )
        }
        Spacer(Modifier.height(16.dp))
        Button(onClick = onStart, enabled = prefs.consented, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.continue_guest))
        }
        OutlinedButton(
            onClick = { prefs.setPrivateMode(true); onStart() },
            enabled = prefs.consented,
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.private_mode)) }
        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sign_in_soon))
        }
    }
    }
}

@Composable
fun HomeScreen(
    prefs: AppPrefs,
    onSettings: () -> Unit,
    onWeb: (String) -> Unit,
    onPlay: (MediaItem) -> Unit,
    onOpenPlayer: () -> Unit
) {
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var hasAccess by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<MediaItem>>(emptyList()) }

    val perms = if (Build.VERSION.SDK_INT >= 33)
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_AUDIO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    // Media access is asked only when the user wants results from this phone, and the reason is shown first
    var permanentlyDenied by remember { mutableStateOf(false) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        hasAccess = grants.values.any { granted -> granted }
        val activity = ctx as? Activity
        // After a second refusal Android stops showing the dialog: point the user to the app settings instead
        permanentlyDenied = !hasAccess && activity != null &&
            !ActivityCompat.shouldShowRequestPermissionRationale(activity, perms[0])
    }
    fun askForMedia() = permLauncher.launch(perms)

    // Waits for typing to pause, so each keystroke does not start a new search
    LaunchedEffect(query, hasAccess) {
        delay(250)
        results = if (hasAccess && query.isNotBlank())
            withContext(Dispatchers.IO) { MediaSearch.search(ctx, query) }
        else emptyList()
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.app_name),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onSettings) { Text(stringResource(R.string.settings)) }
        }

        // Now-playing strip: shows what is playing while you browse, and opens the player
        if (Playback.hasEngine) {
            Row(
                Modifier.fillMaxWidth().clickable { onOpenPlayer() }.padding(vertical = 8.dp),
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
            placeholder = { Text(stringResource(R.string.search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onWeb(query) })
        )

        // Autocomplete from recent searches (empty box shows the latest five)
        if (!prefs.privateMode) prefs.recent
            .filter { it.contains(query, ignoreCase = true) && it != query }
            .take(5)
            .forEach { s ->
                Text(
                    s,
                    modifier = Modifier.fillMaxWidth().clickable { query = s }.padding(vertical = 8.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

        if (query.isNotBlank()) {
            Button(onClick = { onWeb(query) }, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(stringResource(R.string.search_web, query))
            }
        }

        if (!hasAccess && query.isNotBlank()) {
            Text(stringResource(R.string.media_why), style = MaterialTheme.typography.bodySmall)
            if (permanentlyDenied) {
                TextButton(onClick = {
                    ctx.startActivity(
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
                    )
                }) { Text(stringResource(R.string.open_settings)) }
            } else {
                TextButton(onClick = { askForMedia() }) { Text(stringResource(R.string.allow_media)) }
            }
        }

        LazyColumn(Modifier.weight(1f)) {
            items(results) { item ->
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPlay(item) }
                        .padding(vertical = 12.dp)
                ) {
                    Text(item.title, maxLines = 1)
                    Text(
                        if (item.isVideo) stringResource(R.string.video) else stringResource(R.string.audio),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

@Composable
fun WebSearchScreen(url: String, privateMode: Boolean, webScripts: Boolean, onBack: () -> Unit) {
    val holder = remember { WebHolder() }

    BackHandler {
        val w = holder.view
        if (w != null && w.canGoBack()) w.goBack() else onBack()
    }

    // Private Mode clears web cookies and cache when leaving the search page
    DisposableEffect(Unit) {
        onDispose {
            if (privateMode) {
                holder.view?.clearCache(true)
                holder.view?.clearHistory()
                CookieManager.getInstance().removeAllCookies(null)
                WebStorage.getInstance().deleteAllData()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = webScripts
                    settings.setJavaScriptCanOpenWindowsAutomatically(false)
                    settings.setGeolocationEnabled(false)
                    settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW)
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                        WebSettingsCompat.setSafeBrowsingEnabled(settings, true)
                    }
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportMultipleWindows(false)
                    webViewClient = object : WebViewClient() {
                        // Only web addresses load here. App links such as intent: or market: are blocked.
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            return !WebPolicy.isAllowedPage(request.url.toString())
                        }

                        // Certificate errors are never accepted: the page stays blocked
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                            handler.cancel()
                        }
                    }
                    loadUrl(url)
                    holder.view = this
                }
            }
        )
    }
}

@Composable
fun SettingsScreen(prefs: AppPrefs, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        TextButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)

        Section(stringResource(R.string.account)) {
            Text(stringResource(R.string.signed_in_as, stringResource(R.string.guest)))
        }
        Section(stringResource(R.string.theme)) {
            HTheme.entries.forEach { t -> OptionRow(t.label, prefs.theme == t) { prefs.setTheme(t) } }
        }
        Section(stringResource(R.string.font)) {
            HFont.entries.forEach { f -> OptionRow(f.label, prefs.font == f) { prefs.setFont(f) } }
        }
        Section(stringResource(R.string.language)) {
            LANGUAGES.forEach { (tag, name) ->
                OptionRow(name, prefs.language == tag) {
                    prefs.setLanguage(tag)
                    AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
                }
            }
        }
        Section(stringResource(R.string.engine_title)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.smart_engine), modifier = Modifier.weight(1f))
                Switch(checked = prefs.smartEngine, onCheckedChange = prefs::setSmartEngine)
            }
            Text(stringResource(R.string.smart_engine_note), style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = prefs.learner::reset) { Text(stringResource(R.string.reset_learning)) }
            VoidPresets.ALL.forEach { p ->
                OptionRow("${p.name}: ${p.note}", prefs.engineId == p.id) { prefs.setEngine(p.id) }
            }
        }
        Section(stringResource(R.string.privacy)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.web_scripts))
                    Text(stringResource(R.string.web_scripts_note), style = MaterialTheme.typography.bodySmall)
                }
                Switch(checked = prefs.webScripts, onCheckedChange = prefs::setWebScripts)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.private_mode), modifier = Modifier.weight(1f))
                Switch(checked = prefs.privateMode, onCheckedChange = prefs::setPrivateMode)
            }
            TextButton(onClick = prefs::clearRecent) { Text(stringResource(R.string.clear_recent)) }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    Spacer(Modifier.height(20.dp))
    Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
    Column(content = content)
}

@Composable
private fun OptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary)
    }
}
