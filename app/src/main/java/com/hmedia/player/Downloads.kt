package com.hmedia.player

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.CookieManager
import android.webkit.URLUtil
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hmedia.player.web.WebPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// A finished download: where it is in the media store, its name, type, size and time
data class DlFile(val downloadId: Long, val uri: Uri, val name: String, val mime: String, val size: Long, val modifiedMs: Long)

// A download that is still running, waiting, paused or has failed
data class DlActive(val id: Long, val title: String, val status: Int, val soFar: Long, val total: Long)

// Downloads from the web screen go to the phone's Downloads folder through Android's download manager,
// with a notification. Android 10 and newer need no storage permission for this.
object AppDownloads {
    val supported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    // Starts the download. Returns the file name, or null if it cannot be started here.
    fun enqueue(ctx: Context, url: String, userAgent: String?, contentDisposition: String?, mimeType: String?): String? {
        if (!supported || !WebPolicy.isAllowedPage(url)) return null
        val name = URLUtil.guessFileName(url, contentDisposition, mimeType).replace('/', '_')
        return try {
            val request = DownloadManager.Request(Uri.parse(url))
                .setTitle(name)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name)
            if (!mimeType.isNullOrBlank()) request.setMimeType(mimeType)
            if (!userAgent.isNullOrBlank()) request.addRequestHeader("User-Agent", userAgent)
            CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            name
        } catch (e: RuntimeException) {
            null
        }
    }

    // Running downloads, and the finished ones with their current name and size from the media store
    fun load(ctx: Context): Pair<List<DlActive>, List<DlFile>> {
        if (!supported) return Pair(emptyList(), emptyList())
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val active = ArrayList<DlActive>()
        val done = ArrayList<DlFile>()
        val gone = ArrayList<Long>()
        try {
            dm.query(DownloadManager.Query())?.use { c ->
                val idCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
                val titleCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
                val statusCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
                val totalCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                val soFarCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                val modCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_LAST_MODIFIED_TIMESTAMP)
                val providerCol = c.getColumnIndex(DownloadManager.COLUMN_MEDIAPROVIDER_URI)
                val typeCol = c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)
                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val title = c.getString(titleCol) ?: ""
                    val status = c.getInt(statusCol)
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        val uri = (if (providerCol >= 0) c.getString(providerCol) else null)?.takeIf { it.isNotBlank() }?.let { Uri.parse(it) }
                            ?: dm.getUriForDownloadedFile(id)
                            ?: continue
                        val mime = c.getString(typeCol)?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
                        val file = readFile(ctx, id, uri, title, mime, c.getLong(modCol))
                        if (file == null) gone += id else done += file
                    } else {
                        active += DlActive(id, title, status, c.getLong(soFarCol), c.getLong(totalCol))
                    }
                }
            }
        } catch (e: RuntimeException) {
            // Keep whatever was read; nothing is removed after an error
            gone.clear()
        }
        // Files deleted outside the download manager leave an empty entry behind; tidy it
        gone.forEach { runCatching { dm.remove(it) } }
        return Pair(active, done.sortedByDescending { it.modifiedMs })
    }

    // Null only when the media store has no such file any more
    private fun readFile(ctx: Context, id: Long, uri: Uri, title: String, mime: String, modified: Long): DlFile? {
        val fallback = DlFile(id, uri, title, mime, 0L, modified)
        return try {
            ctx.contentResolver.query(
                uri,
                arrayOf(
                    MediaStore.MediaColumns.DISPLAY_NAME,
                    MediaStore.MediaColumns.SIZE,
                    MediaStore.MediaColumns.MIME_TYPE,
                    MediaStore.MediaColumns.DATE_MODIFIED
                ),
                null, null, null
            )?.use { c ->
                if (!c.moveToFirst()) {
                    null
                } else {
                    DlFile(
                        id,
                        uri,
                        c.getString(0) ?: title,
                        c.getString(2)?.takeIf { it.isNotBlank() } ?: mime,
                        c.getLong(1),
                        (c.getLong(3) * 1000L).takeIf { it > 0 } ?: modified
                    )
                }
            } ?: fallback
        } catch (e: RuntimeException) {
            fallback
        }
    }

    fun cancel(ctx: Context, id: Long) {
        runCatching { (ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).remove(id) }
    }

    @Suppress("DEPRECATION")
    fun totalSpace(): Long = runCatching { StatFs(Environment.getExternalStorageDirectory().path).totalBytes }.getOrDefault(0L)
}

private fun kindOf(file: DlFile): Int = when {
    file.mime.startsWith("image/") -> 1
    file.mime.startsWith("video/") -> 2
    file.mime.startsWith("audio/") -> 3
    else -> 4
}

private fun dayKey(ms: Long): String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date(ms))

@Composable
private fun dayLabel(ms: Long): String {
    val now = System.currentTimeMillis()
    val pretty = SimpleDateFormat("d MMM yyyy", Locale.getDefault()).format(Date(ms))
    return when {
        now - ms < 2 * 60_000L -> stringResource(R.string.downloads_just_now)
        dayKey(ms) == dayKey(now) -> stringResource(R.string.downloads_today)
        dayKey(ms) == dayKey(now - 86_400_000L) -> stringResource(R.string.downloads_yesterday, pretty)
        else -> pretty
    }
}

private sealed interface DlRow {
    data class Header(val ms: Long, val running: Boolean = false) : DlRow
    data class Images(val files: List<DlFile>) : DlRow
    data class FileItem(val file: DlFile) : DlRow
    data class ActiveItem(val item: DlActive) : DlRow
}

// What the web screen downloaded: running downloads on top, then the files by day, images as a grid.
// Tap a file to play or open it; the three dots give rename, details, share and delete.
@Composable
fun DownloadsScreen(prefs: AppPrefs, onPlayed: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    BackHandler { onBack() }
    var active by remember { mutableStateOf<List<DlActive>>(emptyList()) }
    var files by remember { mutableStateOf<List<DlFile>>(emptyList()) }
    var loaded by remember { mutableStateOf(false) }
    var kind by rememberSaveable { mutableStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var reload by remember { mutableStateOf(0) }
    var actionsFor by remember { mutableStateOf<DlFile?>(null) }
    val resumeTick = rememberResumeTick()
    val totalSpace = remember { AppDownloads.totalSpace() }

    // Refreshes while the screen is open: quickly while something is downloading
    LaunchedEffect(reload, resumeTick) {
        while (true) {
            val (a, f) = withContext(Dispatchers.IO) { AppDownloads.load(ctx) }
            active = a
            files = f
            loaded = true
            delay(if (a.isEmpty()) 4000L else 1000L)
        }
    }

    fun open(file: DlFile) {
        val isVideo = file.mime.startsWith("video/")
        if (isVideo || file.mime.startsWith("audio/")) {
            val item = MediaItem(title = file.name, uri = file.uri, isVideo = isVideo, sizeBytes = file.size)
            if (Playback.playQueue(ctx, prefs, listOf(item), 0)) onPlayed()
            else Toast.makeText(ctx, Playback.status, Toast.LENGTH_SHORT).show()
        } else {
            openInAnotherApp(ctx, file.uri, file.mime)
        }
    }

    val shown = remember(files, kind, query) {
        files.filter { (kind == 0 || kindOf(it) == kind) && (query.isBlank() || it.name.contains(query, ignoreCase = true)) }
    }
    val rows = remember(shown, active, kind, query) {
        buildList<DlRow> {
            if (kind == 0 && query.isBlank() && active.isNotEmpty()) {
                add(DlRow.Header(0L, running = true))
                active.forEach { add(DlRow.ActiveItem(it)) }
            }
            shown.groupBy { dayKey(it.modifiedMs) }.values.forEach { group ->
                add(DlRow.Header(group.first().modifiedMs))
                group.filter { kindOf(it) == 1 }.chunked(2).forEach { add(DlRow.Images(it)) }
                group.filter { kindOf(it) != 1 }.forEach { add(DlRow.FileItem(it)) }
            }
        }
    }
    val used = files.sumOf { it.size }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
        Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back), tint = Color.White)
            }
            Text(
                stringResource(R.string.downloads_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.downloads_search_hint), tint = Color.White)
            }
        }
        Text(
            stringResource(R.string.downloads_storage, bytesLabel(used), if (totalSpace > 0) bytesLabel(totalSpace) else "—"),
            style = MaterialTheme.typography.bodyMedium,
            color = MutedText,
            modifier = Modifier.padding(start = 12.dp, top = 4.dp, bottom = 10.dp)
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x22FFFFFF)))
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.downloads_search_hint), color = MutedText) },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
            )
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                R.string.downloads_all, R.string.downloads_images, R.string.downloads_videos,
                R.string.downloads_audio, R.string.downloads_other
            ).forEachIndexed { index, label ->
                KindChip(stringResource(label), kind == index) { kind = index }
            }
        }
        if (!AppDownloads.supported) {
            Text(stringResource(R.string.downloads_unsupported), style = MaterialTheme.typography.bodyMedium, color = MutedText)
        } else if (loaded && rows.isEmpty()) {
            Text(
                stringResource(R.string.downloads_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MutedText,
                modifier = Modifier.padding(top = 12.dp)
            )
        }
        LazyColumn(Modifier.weight(1f)) {
            items(rows) { row ->
                when (row) {
                    is DlRow.Header -> Text(
                        if (row.running) stringResource(R.string.downloads_active) else dayLabel(row.ms),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        modifier = Modifier.padding(top = 18.dp, bottom = 10.dp)
                    )
                    is DlRow.Images -> Row(
                        Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        row.files.forEach { file ->
                            Box(
                                Modifier.weight(1f).aspectRatio(1f).clip(RoundedCornerShape(12.dp))
                                    .clickable { open(file) }
                            ) {
                                ThumbOrBanner(file.uri, Modifier.fillMaxSize())
                                Box(
                                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(34.dp).clip(CircleShape)
                                        .background(Color(0x99000000)).clickable { actionsFor = file },
                                    contentAlignment = Alignment.Center
                                ) { MoreDots() }
                            }
                        }
                        if (row.files.size == 1) Spacer(Modifier.weight(1f))
                    }
                    is DlRow.FileItem -> FileRow(row.file, onOpen = { open(row.file) }, onMore = { actionsFor = row.file })
                    is DlRow.ActiveItem -> ActiveRow(row.item) {
                        AppDownloads.cancel(ctx, row.item.id)
                        reload++
                    }
                }
            }
        }
    }

    actionsFor?.let { file ->
        FileActionsSheet(
            target = FileTarget(file.uri, file.name, file.mime),
            onDismiss = { actionsFor = null },
            onChanged = { reload++ }
        )
    }
}

@Composable
private fun KindChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(20.dp))
            .background(if (selected) MaterialTheme.colorScheme.primary else Color(0xFF16161A))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(label, style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun FileRow(file: DlFile, onOpen: () -> Unit, onMore: () -> Unit) {
    val icon = when (kindOf(file)) {
        2 -> R.drawable.ic_video
        3 -> R.drawable.ic_music
        else -> R.drawable.ic_storage
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).clip(CircleShape).background(Color(0xFF1A1A20)), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, color = Color.White)
            if (file.size > 0) Text(sizeLabel(file.size), style = MaterialTheme.typography.bodySmall, color = MutedText)
        }
        Box(
            Modifier.size(width = 40.dp, height = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onMore),
            contentAlignment = Alignment.Center
        ) { MoreDots() }
    }
}

@Composable
private fun ActiveRow(item: DlActive, onCancel: () -> Unit) {
    val failed = item.status == DownloadManager.STATUS_FAILED
    val running = item.status == DownloadManager.STATUS_RUNNING && item.total > 0
    val percent = if (item.total > 0) (item.soFar * 100 / item.total).toInt().coerceIn(0, 100) else 0
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium, color = Color.White)
            if (!failed) {
                if (running) LinearProgressIndicator(progress = { percent / 100f }, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp))
            }
            Text(
                when {
                    failed -> stringResource(R.string.downloads_failed)
                    item.status == DownloadManager.STATUS_PAUSED -> stringResource(R.string.downloads_paused)
                    running -> stringResource(R.string.downloads_progress, percent)
                    else -> stringResource(R.string.downloads_waiting)
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (failed) MaterialTheme.colorScheme.error else MutedText
            )
        }
        IconButton(onClick = onCancel) {
            Icon(painterResource(R.drawable.ic_close), contentDescription = stringResource(R.string.file_cancel), tint = Color.White)
        }
    }
}
