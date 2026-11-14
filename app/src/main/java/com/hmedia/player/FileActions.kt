package com.hmedia.player

import android.app.Activity
import android.app.RecoverableSecurityException
import android.content.ActivityNotFoundException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

// A file the actions sheet works on: its media-store address, the name to show and a type to use if the phone gives none
data class FileTarget(val uri: Uri, val title: String, val fallbackMime: String)

private data class FileInfo(val name: String, val sizeBytes: Long, val mime: String, val modifiedMs: Long, val path: String)

@Suppress("DEPRECATION")
private fun readInfo(ctx: Context, target: FileTarget): FileInfo {
    var name = target.title
    var size = 0L
    var mime = target.fallbackMime
    var modified = 0L
    var path = ""
    runCatching {
        ctx.contentResolver.query(
            target.uri,
            arrayOf(
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_MODIFIED,
                MediaStore.MediaColumns.DATA
            ),
            null, null, null
        )?.use { c ->
            if (c.moveToFirst()) {
                c.getString(0)?.let { name = it }
                size = c.getLong(1)
                c.getString(2)?.takeIf { it.isNotBlank() }?.let { mime = it }
                modified = c.getLong(3) * 1000L
                c.getString(4)?.let { path = it }
            }
        }
    }
    return FileInfo(name, size, mime, modified, path)
}

private fun launchChooser(ctx: Context, intent: Intent, title: String) {
    val chooser = Intent.createChooser(intent, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        ctx.startActivity(chooser)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(ctx, R.string.file_no_app, Toast.LENGTH_SHORT).show()
    } catch (e: SecurityException) {
        Toast.makeText(ctx, R.string.file_no_app, Toast.LENGTH_SHORT).show()
    }
}

// Opens the file in any app that can show it
fun openInAnotherApp(ctx: Context, uri: Uri, mime: String) {
    val view = Intent(Intent.ACTION_VIEW)
        .setDataAndType(uri, mime.ifBlank { "*/*" })
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    launchChooser(ctx, view, ctx.getString(R.string.file_open_with))
}

fun shareFile(ctx: Context, uri: Uri, mime: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime.ifBlank { "*/*" })
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    launchChooser(ctx, send, ctx.getString(R.string.file_share))
}

private enum class Pane { NONE, RENAME, DETAILS, DELETE }

// The list of actions for one file: rename, open in another app, share, details and delete.
// Files from other apps need the phone's own permission prompt, which this sheet starts when needed.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionsSheet(target: FileTarget, onDismiss: () -> Unit, onChanged: () -> Unit) {
    val ctx = LocalContext.current
    var pane by remember { mutableStateOf(Pane.NONE) }
    var info by remember { mutableStateOf<FileInfo?>(null) }
    var error by remember { mutableStateOf("") }
    var after by remember { mutableStateOf<(() -> Unit)?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val action = after
        after = null
        if (result.resultCode == Activity.RESULT_OK) action?.invoke()
    }

    LaunchedEffect(target.uri) { info = withContext(Dispatchers.IO) { readInfo(ctx, target) } }

    val shownName = info?.name ?: target.title
    val mime = info?.mime ?: target.fallbackMime

    fun writeSender(e: SecurityException): IntentSender? = when {
        Build.VERSION.SDK_INT >= 30 -> MediaStore.createWriteRequest(ctx.contentResolver, listOf(target.uri)).intentSender
        Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException -> e.userAction.actionIntent.intentSender
        else -> null
    }

    fun deleteSender(e: SecurityException): IntentSender? = when {
        Build.VERSION.SDK_INT >= 30 -> MediaStore.createDeleteRequest(ctx.contentResolver, listOf(target.uri)).intentSender
        Build.VERSION.SDK_INT >= 29 && e is RecoverableSecurityException -> e.userAction.actionIntent.intentSender
        else -> null
    }

    fun applyRename(newName: String) {
        val values = ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }
        fun attempt(allowPrompt: Boolean) {
            try {
                if (ctx.contentResolver.update(target.uri, values, null, null) > 0) {
                    onChanged()
                    onDismiss()
                } else {
                    error = ctx.getString(R.string.file_rename_failed)
                }
            } catch (e: SecurityException) {
                val sender = if (allowPrompt) writeSender(e) else null
                if (sender != null) {
                    after = { attempt(false) }
                    launcher.launch(IntentSenderRequest.Builder(sender).build())
                } else {
                    error = ctx.getString(R.string.file_rename_failed)
                }
            } catch (e: RuntimeException) {
                error = ctx.getString(R.string.file_rename_failed)
            }
        }
        attempt(true)
    }

    fun finishDelete() {
        if (Playback.currentUri == target.uri) Playback.stop()
        onChanged()
        onDismiss()
    }

    fun applyDelete() {
        fun attempt(allowPrompt: Boolean) {
            try {
                if (ctx.contentResolver.delete(target.uri, null, null) > 0) finishDelete()
                else error = ctx.getString(R.string.file_delete_failed)
            } catch (e: SecurityException) {
                val sender = if (allowPrompt) deleteSender(e) else null
                if (sender != null) {
                    // From Android 11 the system deletes the file itself once the person agrees
                    after = { if (Build.VERSION.SDK_INT >= 30) finishDelete() else attempt(false) }
                    launcher.launch(IntentSenderRequest.Builder(sender).build())
                } else {
                    error = ctx.getString(R.string.file_delete_failed)
                }
            } catch (e: RuntimeException) {
                error = ctx.getString(R.string.file_delete_failed)
            }
        }
        attempt(true)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Color(0xFF111115),
        contentColor = Color.White
    ) {
        Text(
            shownName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        ActionRow(R.drawable.ic_edit, R.string.file_rename, false) { error = ""; pane = Pane.RENAME }
        ActionRow(R.drawable.ic_play, R.string.file_open_with, false) { openInAnotherApp(ctx, target.uri, mime); onDismiss() }
        ActionRow(R.drawable.ic_share, R.string.file_share, false) { shareFile(ctx, target.uri, mime); onDismiss() }
        ActionRow(R.drawable.ic_info, R.string.file_details, false) { pane = Pane.DETAILS }
        ActionRow(R.drawable.ic_delete, R.string.file_delete, true) { error = ""; pane = Pane.DELETE }
        Spacer(Modifier.height(24.dp))
    }

    when (pane) {
        Pane.NONE -> Unit
        Pane.RENAME -> {
            var newName by remember { mutableStateOf(shownName) }
            AlertDialog(
                onDismissRequest = { pane = Pane.NONE },
                title = { Text(stringResource(R.string.file_rename)) },
                text = {
                    Column {
                        OutlinedTextField(
                            value = newName,
                            onValueChange = { newName = it; error = "" },
                            label = { Text(stringResource(R.string.file_new_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                },
                confirmButton = {
                    TextButton(
                        enabled = newName.isNotBlank() && !newName.contains('/') && newName.trim() != shownName,
                        onClick = { applyRename(newName.trim()) }
                    ) { Text(stringResource(R.string.file_ok)) }
                },
                dismissButton = { TextButton(onClick = { pane = Pane.NONE }) { Text(stringResource(R.string.file_cancel)) } }
            )
        }
        Pane.DETAILS -> {
            val i = info
            AlertDialog(
                onDismissRequest = { pane = Pane.NONE },
                title = { Text(stringResource(R.string.file_details)) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailLine(R.string.file_detail_name, shownName)
                        DetailLine(R.string.file_detail_type, mime)
                        if (i != null && i.sizeBytes > 0) DetailLine(R.string.file_detail_size, bytesLabel(i.sizeBytes))
                        if (i != null && i.modifiedMs > 0) DetailLine(R.string.file_detail_modified, DateFormat.getDateTimeInstance().format(Date(i.modifiedMs)))
                        if (i != null && i.path.isNotBlank()) DetailLine(R.string.file_detail_location, i.path)
                    }
                },
                confirmButton = { TextButton(onClick = { pane = Pane.NONE }) { Text(stringResource(R.string.file_close)) } }
            )
        }
        Pane.DELETE -> AlertDialog(
            onDismissRequest = { pane = Pane.NONE },
            title = { Text(stringResource(R.string.file_delete)) },
            text = {
                Column {
                    Text(stringResource(R.string.file_delete_confirm, shownName))
                    if (error.isNotEmpty()) Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = { TextButton(onClick = { applyDelete() }) { Text(stringResource(R.string.file_delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { pane = Pane.NONE }) { Text(stringResource(R.string.file_cancel)) } }
        )
    }
}

@Composable
private fun ActionRow(icon: Int, label: Int, danger: Boolean, onClick: () -> Unit) {
    val color = if (danger) MaterialTheme.colorScheme.error else Color.White
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(18.dp))
        Text(stringResource(label), style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun DetailLine(label: Int, value: String) {
    Column {
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, color = MutedText)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// 130.83 MB, 116.75 GB
fun bytesLabel(bytes: Long): String = when {
    bytes >= 1L shl 30 -> "%.2f GB".format(bytes / 1073741824.0)
    bytes >= 1L shl 20 -> "%.2f MB".format(bytes / 1048576.0)
    bytes >= 1024L -> "%.0f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
