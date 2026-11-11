package com.hmedia.player

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
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

// Video or music library built from the real files on the phone.
// Tapping a file plays the filtered list in order, so Next and Previous work.
@Composable
fun LibraryScreen(prefs: AppPrefs, isVideo: Boolean, onPlayed: () -> Unit) {
    val ctx = LocalContext.current
    val resumeTick = rememberResumeTick()
    var hasAccess by remember { mutableStateOf(MediaSearch.hasAccess(ctx)) }
    var allItems by remember { mutableStateOf<List<MediaItem>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var filter by rememberSaveable { mutableStateOf("") }
    var message by remember { mutableStateOf("") }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        hasAccess = grants.values.any { it } || MediaSearch.hasAccess(ctx)
    }

    LaunchedEffect(resumeTick) {
        hasAccess = MediaSearch.hasAccess(ctx)
    }

    LaunchedEffect(hasAccess, isVideo, resumeTick) {
        allItems = if (hasAccess) withContext(Dispatchers.IO) { MediaSearch.list(ctx, isVideo) } else emptyList()
        loaded = true
    }

    val shown = allItems.filter { it.title.contains(filter, ignoreCase = true) }

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
            OutlinedTextField(
                value = filter,
                onValueChange = { filter = it },
                placeholder = { Text(stringResource(R.string.lib_filter)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
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
            } else if (shown.isEmpty()) {
                Text(
                    stringResource(R.string.lib_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 16.dp)
                )
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    itemsIndexed(shown) { index, item ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable {
                                    message = ""
                                    if (Playback.playQueue(ctx, prefs, shown, index)) {
                                        onPlayed()
                                    } else {
                                        message = Playback.status
                                    }
                                }
                                .padding(vertical = 12.dp)
                        ) {
                            Text(
                                item.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyLarge
                            )
                            Text(
                                stringResource(if (item.isVideo) R.string.video else R.string.audio),
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
