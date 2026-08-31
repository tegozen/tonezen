package com.tonezen.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.components.CheckCircleGlyph
import com.tonezen.app.ui.components.ChevronRightGlyph
import com.tonezen.app.ui.components.StatusChip
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun SyncStatusCard(
    lastSyncTime: String?,
    pendingSyncCount: Int,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TonezenSurfaceRaised)
            .border(1.dp, TonezenBorder, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CheckCircleGlyph(tint = TonezenTeal, size = 22.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                "Всё в порядке",
                color = TonezenInk,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (lastSyncTime != null) {
                    "Последняя синхронизация: сегодня, ${lastSyncTime}"
                } else {
                    "Синхронизация ещё не выполнялась"
                },
                color = TonezenMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            if (pendingSyncCount > 0) {
                StatusChip(label = "Ожидает", tone = TonezenAmber)
            }
        }
    }
}

@Composable
internal fun SignOutCard(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TonezenSurfaceRaised)
            .border(1.dp, TonezenBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Выйти",
            color = TonezenError,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun SettingsGroup(
    items: List<SettingsItem>,
    onItemClick: (ProfileSettingsAction) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TonezenSurfaceRaised)
            .border(1.dp, TonezenBorder, RoundedCornerShape(16.dp)),
    ) {
        items.forEachIndexed { index, item ->
            SettingsRow(
                title = item.title,
                subtitle = item.subtitle,
                icon = item.icon,
                badge = item.badge,
                onClick = { onItemClick(item.action) },
            )
            if (index < items.lastIndex) {
                HorizontalDivider(color = TonezenBorder.copy(alpha = 0.65f))
            }
        }
    }
}

@Composable
private fun SettingsRow(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    badge: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f),
        ) {
            icon()
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = TonezenInk, fontWeight = FontWeight.Medium)
                Text(subtitle, color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            if (badge > 0) StatusChip(label = badge.toString(), tone = TonezenAmber)
            ChevronRightGlyph()
        }
    }
}

internal data class SettingsItem(
    val action: ProfileSettingsAction,
    val title: String,
    val subtitle: String,
    val icon: @Composable () -> Unit,
    val badge: Int = 0,
)
