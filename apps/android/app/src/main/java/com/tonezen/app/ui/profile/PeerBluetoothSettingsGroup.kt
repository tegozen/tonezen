package com.tonezen.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import com.tonezen.app.ui.components.SyncGlyph
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurfaceRaised

@Composable
internal fun PeerBluetoothSettingsGroup(
    enabled: Boolean,
    onAcceptClick: () -> Unit,
    onSendClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TonezenSurfaceRaised)
            .border(1.dp, TonezenBorder, RoundedCornerShape(16.dp)),
    ) {
        PeerBluetoothRow(
            title = "Принять",
            subtitle = "Ждать прогресс с другого устройства",
            enabled = enabled,
            onClick = onAcceptClick,
        )
        HorizontalDivider(color = TonezenBorder.copy(alpha = 0.65f))
        PeerBluetoothRow(
            title = "Отправить",
            subtitle = "Передать прогресс по блютус",
            enabled = enabled,
            onClick = onSendClick,
        )
    }
}

@Composable
private fun PeerBluetoothRow(
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SyncGlyph(tint = if (enabled) TonezenInk else TonezenMuted)
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title,
                color = if (enabled) TonezenInk else TonezenMuted,
                fontWeight = FontWeight.Medium,
            )
            Text(subtitle, color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
