package com.tonezen.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenChromeBarBackground
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.trackCoverBrush

enum class BottomDestination(val labelRes: Int) {
    Library(R.string.nav_library),
    Downloads(R.string.nav_downloads),
    Profile(R.string.nav_profile),
}

@Composable
internal fun TonezenBottomNavigation(
    selected: BottomDestination,
    onSelect: (BottomDestination) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TonezenChromeBarBackground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomNavItem(BottomDestination.Library, selected) { onSelect(BottomDestination.Library) }
        BottomNavItem(BottomDestination.Downloads, selected) { onSelect(BottomDestination.Downloads) }
        BottomNavItem(BottomDestination.Profile, selected) { onSelect(BottomDestination.Profile) }
    }
}

@Composable
private fun BottomNavItem(
    destination: BottomDestination,
    selected: BottomDestination,
    onClick: () -> Unit,
) {
    val active = destination == selected
    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (active) TonezenTeal.copy(alpha = 0.95f) else Color.Transparent)
                .border(
                    BorderStroke(1.dp, if (active) TonezenTeal else TonezenMuted.copy(alpha = 0.7f)),
                    RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            val tint = if (active) TonezenAppBg else TonezenMuted
            when (destination) {
                BottomDestination.Library -> LibraryGlyph(tint = tint)
                BottomDestination.Downloads -> DownloadGlyph(tint = tint)
                BottomDestination.Profile -> ProfileGlyph(tint = tint)
            }
        }
        Text(
            text = stringResource(destination.labelRes),
            color = if (active) TonezenTeal else TonezenMuted,
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

@Composable
internal fun MiniPlayer(
    title: String?,
    subtitle: String?,
    coverSeed: String?,
    enabled: Boolean,
    isPlaying: Boolean = false,
    positionMs: Long = 0L,
    durationMs: Long = 0L,
    onBarClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
) {
    if (!enabled || title == null) return
    val progress = if (durationMs > 0L) {
        (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(TonezenChromeBarBackground),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .background(Color.White.copy(alpha = 0.08f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress)
                    .height(3.dp)
                    .background(TonezenTeal),
            )
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onBarClick),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MiniCover(seed = coverSeed ?: title, title = title)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        title,
                        color = TonezenInk,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            subtitle,
                            color = TonezenMuted,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(TonezenTeal.copy(alpha = 0.18f))
                    .border(BorderStroke(1.dp, TonezenTeal.copy(alpha = 0.35f)), CircleShape)
                    .clickable(onClick = onPlayPauseClick),
                contentAlignment = Alignment.Center,
            ) {
                if (isPlaying) {
                    PauseGlyph(tint = TonezenTeal, size = 20.dp)
                } else {
                    PlayGlyph(tint = TonezenTeal, size = 20.dp)
                }
            }
        }
    }
}

@Composable
private fun MiniCover(seed: String, title: String) {
    val brush = remember(seed) { trackCoverBrush(seed) }
    val initials = remember(title) {
        title.trim()
            .split(Regex("\\s+"))
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
            .joinToString("")
            .ifBlank { "♪" }
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(brush)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(initials, color = TonezenAmber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
    }
}
