package com.hmedia.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.blur

// Secondary text colour of the reference design
val MutedText = Color(0xFF9A9AA2)
private val PanelDark = Color(0xFF16161A)

// Top bar: logo, name, search and profile picture
@Composable
fun AppHeader(prefs: AppPrefs, onSearch: (() -> Unit)?, onProfile: (() -> Unit)?) {
    val red = MaterialTheme.colorScheme.primary
    val title = stringResource(R.string.home_title)
    Row(Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Image(painterResource(R.drawable.hmp_logo), contentDescription = null, modifier = Modifier.size(38.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.width(1.dp).height(22.dp).background(Color(0x33FFFFFF)))
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = red, fontWeight = FontWeight.Bold)) { append(title.take(1)) }
                append(title.drop(1))
            },
            style = MaterialTheme.typography.titleLarge,
            color = Color.White,
            maxLines = 1
        )
        Spacer(Modifier.weight(1f))
        if (onSearch != null) {
            IconButton(onClick = onSearch) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.search_title), tint = Color.White)
            }
        }
        ProfileAvatar(prefs, 36.dp, onProfile)
    }
}

// Screen title with the red bar in front of it
@Composable
fun ScreenTitle(text: String) {
    Row(Modifier.padding(top = 18.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(4.dp).height(30.dp).clip(RoundedCornerShape(2.dp)).background(MaterialTheme.colorScheme.primary))
        Spacer(Modifier.width(14.dp))
        Text(text, style = MaterialTheme.typography.headlineMedium, color = Color.White)
    }
}

// Pill-shaped tabs: the selected one is red
@Composable
fun PillTabs(labels: List<Int>, icons: List<Int>, selected: Int, onSelect: (Int) -> Unit) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(30.dp)).background(PanelDark).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        labels.forEachIndexed { index, label ->
            val isSelected = index == selected
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(26.dp))
                    .background(if (isSelected) accent else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painterResource(icons[index]),
                    contentDescription = null,
                    tint = if (isSelected) Color.White else MutedText,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    stringResource(label),
                    style = MaterialTheme.typography.titleSmall,
                    color = if (isSelected) Color.White else MutedText,
                    maxLines = 1
                )
            }
        }
    }
}

// Full-width red button with a soft gradient
@Composable
fun GradientButton(text: String, iconRes: Int?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(54.dp).clip(RoundedCornerShape(27.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF9E0F1B), Color(0xFFE11D2E))))
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (iconRes != null) {
            Icon(painterResource(iconRes), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, color = Color.White)
    }
}

// The three dots at the end of a row
@Composable
fun MoreDots() {
    Canvas(Modifier.size(width = 6.dp, height = 22.dp)) {
        val radius = 2.5.dp.toPx()
        val centerX = size.width / 2f
        val step = (size.height - 2 * radius) / 2f
        for (i in 0..2) {
            drawCircle(color = MutedText, radius = radius, center = Offset(centerX, radius + i * step))
        }
    }
}

// While a video from the phone is playing, its frame is shown blurred behind the whole app, as in the reference.
// Streams and audio keep the normal background.
@Composable
fun VideoBackdrop() {
    val uri = Playback.currentUri
    if (Playback.kind == "video" && uri != null && uri.scheme == "content") {
        Box(Modifier.fillMaxSize()) {
            ThumbOrBanner(uri, Modifier.fillMaxSize().blur(28.dp))
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(listOf(Color(0xCC0B0B0F), Color(0xF20B0B0F)))
                )
            )
        }
    }
}
