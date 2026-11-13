package com.hmedia.player

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private val GENDERS = listOf(
    "male" to R.string.gender_male,
    "female" to R.string.gender_female,
    "other" to R.string.gender_other,
    "none" to R.string.gender_none
)

// Round picture of the guest profile, or the first letter of the name when there is no picture.
@Composable
fun ProfileAvatar(prefs: AppPrefs, size: Dp, onClick: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val photo by produceState<ImageBitmap?>(initialValue = null, key1 = prefs.profilePhoto) {
        value = if (prefs.profilePhoto.isEmpty()) {
            null
        } else {
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(prefs.profilePhoto)?.asImageBitmap() }.getOrNull() }
        }
    }
    val base = Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer)
    Box(if (onClick != null) base.clickable { onClick?.invoke() } else base, contentAlignment = Alignment.Center) {
        val bitmap = photo
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            val initial = prefs.profileName.trim().firstOrNull()?.uppercase() ?: "?"
            Text(initial, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
}

// Reads a picture at a size suitable for an avatar, so a large photo cannot exhaust memory.
private fun decodeProfilePhoto(ctx: Context, uri: Uri): ImageBitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) } ?: return null
    var sample = 1
    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
}

// Guest profile: name, age, gender and a picture, all saved only on this phone.
@Composable
fun ProfileScreen(prefs: AppPrefs, onBack: () -> Unit) {
    val ctx = LocalContext.current
    var name by rememberSaveable { mutableStateOf(prefs.profileName) }
    var age by rememberSaveable { mutableStateOf(prefs.profileAge) }
    var gender by rememberSaveable { mutableStateOf(prefs.profileGender) }
    var message by remember { mutableStateOf("") }
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            saveSquarePhoto(ctx, uri)?.let { prefs.setProfilePhoto(it) }
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_back), contentDescription = stringResource(R.string.back))
            }
            Text(stringResource(R.string.profile_title), style = MaterialTheme.typography.titleLarge)
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(prefs, 88.dp)
            Spacer(Modifier.width(16.dp))
            OutlinedButton(onClick = {
                photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }) {
                Text(stringResource(R.string.profile_photo_choose))
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it.take(40) },
            label = { Text(stringResource(R.string.profile_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            value = age,
            onValueChange = { age = it.filter { c -> c.isDigit() }.take(3) },
            label = { Text(stringResource(R.string.profile_age)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp)
        )
        Text(
            stringResource(R.string.profile_gender),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 16.dp)
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            items(GENDERS) { option ->
                FilterChip(
                    selected = gender == option.first,
                    onClick = { gender = option.first },
                    label = { Text(stringResource(option.second)) }
                )
            }
        }
        Button(
            onClick = {
                val parsed = age.toIntOrNull()
                if (age.isNotEmpty() && (parsed == null || parsed !in 1..120)) {
                    message = ctx.getString(R.string.profile_age_invalid)
                } else {
                    prefs.saveProfile(name, age, gender)
                    message = ctx.getString(R.string.profile_saved)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(top = 20.dp)
        ) {
            Text(stringResource(R.string.profile_save))
        }
        if (message.isNotEmpty()) {
            Text(message, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
        }
        Text(
            stringResource(R.string.profile_privacy_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}

// Saves a square copy of the picked picture inside the app, so it stays after a restart.
// The picture is centred and cropped to a square, up to 512 pixels.
private fun saveSquarePhoto(ctx: Context, uri: Uri): String? = runCatching {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (bounds.outWidth / sample > 1024 || bounds.outHeight / sample > 1024) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    val full = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
        ?: error("The picture could not be read.")
    val side = minOf(full.width, full.height)
    val square = Bitmap.createBitmap(full, (full.width - side) / 2, (full.height - side) / 2, side, side)
    val size = minOf(side, 512)
    val scaled = Bitmap.createScaledBitmap(square, size, size, true)
    val file = File(ctx.filesDir, "profile.jpg")
    file.outputStream().use { scaled.compress(Bitmap.CompressFormat.JPEG, 92, it) }
    file.absolutePath
}.getOrNull()
