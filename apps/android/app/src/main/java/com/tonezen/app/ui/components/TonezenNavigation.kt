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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenTeal

enum class BottomDestination(val labelRes: Int, val glyph: String) {
    Library(R.string.nav_library, "L"),
    Player(R.string.nav_player, "P"),
    Downloads(R.string.nav_downloads, "D"),
    Profile(R.string.nav_profile, "U"),
}

@Composable
internal fun TonezenBottomNavigation(
    selected: BottomDestination,
    onSelect: (BottomDestination) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TonezenSurface.copy(alpha = 0.96f))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.06f)))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomNavItem(BottomDestination.Library, selected) { onSelect(BottomDestination.Library) }
        BottomNavItem(BottomDestination.Player, selected) { onSelect(BottomDestination.Player) }
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
                .clip(if (destination == BottomDestination.Player) CircleShape else RoundedCornerShape(6.dp))
                .background(if (active) TonezenTeal.copy(alpha = 0.95f) else Color.Transparent)
                .border(
                    BorderStroke(1.dp, if (active) TonezenTeal else TonezenMuted.copy(alpha = 0.7f)),
                    if (destination == BottomDestination.Player) CircleShape else RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(destination.glyph, color = if (active) TonezenAppBg else TonezenMuted, style = MaterialTheme.typography.labelSmall)
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
    enabled: Boolean,
    isPlaying: Boolean = false,
    onBarClick: () -> Unit = {},
    onPlayPauseClick: () -> Unit = {},
) {
    if (!enabled || title == null) return
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .clickable(onClick = onBarClick),
        color = TonezenSurface.copy(alpha = 0.96f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            MiniCover()
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TonezenInk, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle.orEmpty(), color = TonezenMuted, style = MaterialTheme.typography.bodySmall, maxLines = 1)
            }
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onPlayPauseClick),
                contentAlignment = Alignment.Center,
            ) {
                if (isPlaying) {
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Box(Modifier.size(width = 3.dp, height = 14.dp).background(TonezenInk, RoundedCornerShape(1.dp)))
                        Box(Modifier.size(width = 3.dp, height = 14.dp).background(TonezenInk, RoundedCornerShape(1.dp)))
                    }
                } else {
                    PlayTriangle(tint = TonezenInk, modifier = Modifier.size(22.dp))
                }
            }
        }
    }
}

@Composable
private fun MiniCover() {
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(listOf(Color(0xFF0B2535), Color(0xFF14213D))))
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.10f)), RoundedCornerShape(8.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(stringResource(R.string.app_name).take(1), color = TonezenAmber, fontWeight = FontWeight.Bold)
    }
}
