package com.tonezen.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
import com.tonezen.app.ui.components.StatusChip
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenGreen
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenScreenBrush
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import dev.chrisbanes.haze.HazeState

@Composable
internal fun SyncSettingsScreen(
    padding: PaddingValues,
    hazeState: HazeState,
    sessionState: SessionState,
    lastSyncTime: String?,
    pendingSyncCount: Int,
    onBack: () -> Unit,
) {
    val online = sessionState == SessionState.AUTHENTICATED_ONLINE

    TonezenFixedHeaderScreen(
        hazeState = hazeState,
        padding = padding,
        onBack = onBack,
        title = {
            Text(
                stringResource(R.string.settings_sync_page_title),
                color = TonezenInk,
                fontWeight = FontWeight.SemiBold,
            )
        },
    ) {
        item {
            SettingsInfoSection(title = stringResource(R.string.settings_sync_what_section)) {
                SettingsInfoRow(
                    title = stringResource(R.string.settings_sync_progress),
                    subtitle = stringResource(R.string.settings_sync_progress_desc),
                )
                SettingsInfoRow(
                    title = stringResource(R.string.settings_sync_favorites),
                    subtitle = stringResource(R.string.settings_sync_favorites_desc),
                )
            }
        }
        item {
            SettingsInfoSection(title = stringResource(R.string.settings_sync_status_section)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    StatusChip(
                        label = if (online) stringResource(R.string.online) else stringResource(R.string.offline),
                        tone = if (online) TonezenGreen else TonezenAmber,
                    )
                    if (pendingSyncCount > 0) {
                        StatusChip(label = stringResource(R.string.pending), tone = TonezenAmber)
                    }
                }
                Text(
                    if (lastSyncTime != null) {
                        stringResource(R.string.last_sync_today_at, lastSyncTime)
                    } else {
                        stringResource(R.string.last_sync_never)
                    },
                    color = TonezenMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        item {
            Text(
                stringResource(R.string.settings_sync_music_local_note),
                color = TonezenMuted,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TonezenSurfaceRaised)
                    .border(1.dp, TonezenBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
            )
        }
    }
}

@Composable
internal fun SettingsInfoSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TonezenSurfaceRaised)
            .border(1.dp, TonezenBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, color = TonezenInk, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
internal fun SettingsInfoRow(
    title: String,
    subtitle: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(title, color = TonezenInk, fontWeight = FontWeight.Medium)
        Text(subtitle, color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
    }
}
