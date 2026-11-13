package com.hmedia.player

import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Bundle
import android.os.Message
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.hmedia.player.web.WebPolicy
import com.hmedia.player.i18n.AppLanguage

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
                AppShell(prefs)
            }
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
        // Scrolls, so every button stays reachable in landscape and at larger text sizes
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
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
                    text = {
                        // The policy is long, so it scrolls inside the dialog
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            Text(stringResource(R.string.privacy_text))
                        }
                    }
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

// The web page. The page and its history are kept across rotation. If the page's rendering process
// crashes, the page is replaced by a message with a Retry button instead of a dead view.
@Composable
fun WebSearchScreen(url: String, privateMode: Boolean, webScripts: Boolean, onBack: () -> Unit) {
    // Page history saved when the screen pauses, restored when it is created again after rotation
    val saved = rememberSaveable { Bundle() }
    var attempt by remember { mutableStateOf(0) }
    var crashed by remember { mutableStateOf(false) }

    if (crashed) {
        BackHandler { onBack() }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back)) }
            Text(stringResource(R.string.web_error_crashed), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = {
                    saved.clear()
                    crashed = false
                    attempt++
                },
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
            ) { Text(stringResource(R.string.web_retry)) }
        }
    } else {
        // A new key builds a fresh WebView
        key(attempt) {
            WebPage(url, privateMode, webScripts, saved, onBack, onCrashed = { crashed = true })
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun WebPage(
    url: String,
    privateMode: Boolean,
    webScripts: Boolean,
    saved: Bundle,
    onBack: () -> Unit,
    onCrashed: () -> Unit
) {
    val holder = remember { WebHolder() }
    val lifecycleOwner = LocalLifecycleOwner.current
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }

    BackHandler {
        val w = holder.view
        if (w != null && w.canGoBack()) w.goBack() else onBack()
    }

    // Saves the page history when the screen pauses (this runs before Android saves the screen state
    // for rotation), and pauses the page's timers while the app is not in front
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            val w = holder.view
            if (w != null) {
                if (event == Lifecycle.Event.ON_PAUSE) {
                    w.saveState(saved)
                    w.onPause()
                } else if (event == Lifecycle.Event.ON_RESUME) {
                    w.onResume()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Leaving the screen releases the WebView. Private Mode first clears cookies, cache and history.
    DisposableEffect(Unit) {
        onDispose {
            val w = holder.view
            if (w != null) {
                if (privateMode) {
                    w.clearCache(true)
                    w.clearHistory()
                    CookieManager.getInstance().removeAllCookies(null)
                    WebStorage.getInstance().deleteAllData()
                }
                w.destroy()
            }
            holder.view = null
        }
    }

    Column(Modifier.fillMaxSize()) {
        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back)) }
        if (loading) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        if (failed) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.web_error_load),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = {
                    failed = false
                    holder.view?.reload()
                }) { Text(stringResource(R.string.web_retry)) }
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxWidth().weight(1f),
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = webScripts
                    settings.setJavaScriptCanOpenWindowsAutomatically(true)
                    settings.setGeolocationEnabled(false)
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
                        WebSettingsCompat.setSafeBrowsingEnabled(settings, true)
                    }
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportMultipleWindows(false)
                    settings.allowFileAccessFromFileURLs = false
                    settings.allowUniversalAccessFromFileURLs = false
                    // Private Mode: pages cannot keep data on the phone
                    settings.domStorageEnabled = !privateMode
                    // Remote debugging of pages is allowed only in debug builds
                    val debuggable = (ctx.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
                    WebView.setWebContentsDebuggingEnabled(debuggable)
                    webViewClient = object : WebViewClient() {
                        // Only web addresses load here. App links such as intent: or market: are blocked.
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            return !WebPolicy.isAllowedPage(request.url.toString())
                        }

                        override fun onPageStarted(view: WebView, pageUrl: String?, favicon: Bitmap?) {
                            failed = false
                        }

                        override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                            if (request.isForMainFrame) failed = true
                        }

                        // A crashed rendering process leaves the WebView unusable. Returning true keeps the app
                        // alive, and the screen swaps the dead view for a message with a Retry button.
                        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                            onCrashed()
                            return true
                        }

                        // Certificate errors are never accepted: the page stays blocked
                        override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                            handler.cancel()
                            failed = true
                        }
                    }
                    // Pages cannot open pop-up windows, use the camera or microphone, or read the location
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView, newProgress: Int) {
                            loading = newProgress < 100
                        }

                        override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message): Boolean = false
                        override fun onPermissionRequest(request: PermissionRequest) = request.deny()
                        override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) =
                            callback.invoke(origin, false, false)
                    }
                    // A download link opens in the phone's own browser, which handles files safely
                    setDownloadListener { downloadUrl, _, _, _, _ ->
                        if (WebPolicy.isAllowedPage(downloadUrl)) {
                            runCatching {
                                ctx.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                )
                            }
                        }
                    }
                    // After rotation the saved page and history come back; otherwise the search page loads
                    if (saved.isEmpty() || restoreState(saved) == null) loadUrl(url)
                    holder.view = this
                }
            }
        )
    }
}

@Composable
fun SettingsScreen(prefs: AppPrefs, onProfile: () -> Unit = {}, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back)) }
        Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall)

        Section(stringResource(R.string.account)) {
            OptionRow(stringResource(R.string.profile_open), false) { onProfile() }
            Text(stringResource(R.string.signed_in_as, stringResource(R.string.guest)))
        }
        Section(stringResource(R.string.theme)) {
            HTheme.entries.forEach { t -> OptionRow(t.label, prefs.theme == t) { prefs.setTheme(t) } }
        }
        Section(stringResource(R.string.font)) {
            HFont.entries.forEach { f -> OptionRow(f.label, prefs.font == f) { prefs.setFont(f) } }
        }
        Section(stringResource(R.string.language)) {
            OptionRow(stringResource(R.string.language_system_default), prefs.language == "system") {
                prefs.setLanguage("system")
                AppLanguage.setLanguage(null)
            }
            AppLanguage.supported.forEach { lang ->
                OptionRow(lang.nativeName, prefs.language == lang.tag) {
                    prefs.setLanguage(lang.tag)
                    AppLanguage.setLanguage(lang.tag)
                }
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
