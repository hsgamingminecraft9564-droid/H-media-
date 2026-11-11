package com.hmedia.player

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.MalformedURLException
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException

private const val MAX_PLAYLIST_CHARS = 5_000_000

// The loaded channels live here, outside the screen, so rotating the phone does not lose them
private object IptvState {
    var channels by mutableStateOf<List<IptvChannel>>(emptyList())
}

// A problem loading the playlist, as a string resource so it follows the app language
private class PlaylistError(val res: Int, vararg val args: Any) : Exception()

// IPTV: the user types the address of a playlist they are allowed to use. Nothing is bundled.
@Composable
fun IptvScreen(prefs: AppPrefs, onPlayed: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var address by rememberSaveable { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("") }
    val channels = IptvState.channels

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(stringResource(R.string.iptv_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.iptv_note), style = MaterialTheme.typography.bodySmall)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = address,
            onValueChange = { address = it },
            placeholder = { Text(stringResource(R.string.iptv_url_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = !loading && address.isNotBlank(),
            onClick = {
                val source = address.trim()
                loading = true
                message = ""
                scope.launch {
                    val result = withContext(Dispatchers.IO) { loadPlaylist(source) }
                    loading = false
                    result.onSuccess { IptvState.channels = it }
                    result.onFailure { error ->
                        IptvState.channels = emptyList()
                        message = if (error is PlaylistError) ctx.getString(error.res, *error.args)
                                  else ctx.getString(R.string.iptv_err_network)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Text(stringResource(if (loading) R.string.iptv_loading else R.string.iptv_load))
        }
        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(
            value = filter,
            onValueChange = { filter = it },
            placeholder = { Text(stringResource(R.string.lib_filter)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        val shown = channels.filter { it.name.contains(filter, ignoreCase = true) }
        if (shown.isEmpty()) {
            Text(
                stringResource(R.string.iptv_empty),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp)
            )
        } else {
            LazyColumn(Modifier.weight(1f)) {
                itemsIndexed(shown) { index, channel ->
                    Column(
                        Modifier.fillMaxWidth()
                            .clickable {
                                message = ""
                                // The shown channels form the queue, so Next and Previous change channel
                                val queue = shown.map { MediaItem(it.name, Uri.parse(it.url), isVideo = true) }
                                if (Playback.playQueue(ctx, prefs, queue, index)) {
                                    onPlayed()
                                } else {
                                    message = Playback.status
                                }
                            }
                            .padding(vertical = 10.dp)
                    ) {
                        Text(
                            channel.name,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        if (channel.group.isNotEmpty()) {
                            Text(
                                channel.group,
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

// Downloads and reads the playlist. Runs on a background thread, with a size limit.
private fun loadPlaylist(address: String): Result<List<IptvChannel>> = try {
    if (!(address.startsWith("https://") || address.startsWith("http://"))) {
        throw PlaylistError(R.string.iptv_err_scheme)
    }
    val conn = URL(address).openConnection() as HttpURLConnection
    conn.connectTimeout = 10_000
    conn.readTimeout = 15_000
    try {
        val code = conn.responseCode
        if (code !in 200..299) throw PlaylistError(R.string.iptv_err_http, code)
        val text = conn.inputStream.bufferedReader().use { reader ->
            val sb = StringBuilder()
            val buf = CharArray(8192)
            while (sb.length < MAX_PLAYLIST_CHARS) {
                val n = reader.read(buf)
                if (n <= 0) break
                sb.append(buf, 0, n)
            }
            sb.toString()
        }
        val channels = M3uParser.parse(text)
        if (channels.isEmpty()) throw PlaylistError(R.string.iptv_err_empty)
        Result.success(channels)
    } finally {
        conn.disconnect()
    }
} catch (e: PlaylistError) {
    Result.failure(e)
} catch (e: MalformedURLException) {
    Result.failure(PlaylistError(R.string.iptv_err_bad_address))
} catch (e: UnknownHostException) {
    Result.failure(PlaylistError(R.string.iptv_err_host))
} catch (e: SocketTimeoutException) {
    Result.failure(PlaylistError(R.string.iptv_err_timeout))
} catch (e: IOException) {
    Result.failure(PlaylistError(R.string.iptv_err_network))
} catch (e: RuntimeException) {
    Result.failure(PlaylistError(R.string.iptv_err_network))
}
