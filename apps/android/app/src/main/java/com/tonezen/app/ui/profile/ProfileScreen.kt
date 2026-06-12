package com.tonezen.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.ui.components.ChevronRightGlyph
import com.tonezen.app.ui.components.IconCircle
import com.tonezen.app.ui.components.LockGlyph
import com.tonezen.app.ui.components.OfflineSyncDialog
import com.tonezen.app.ui.components.OverflowGlyph
import com.tonezen.app.ui.components.ProfileGlyph
import com.tonezen.app.ui.components.SignOutConfirmDialog
import com.tonezen.app.ui.components.StatusChip
import com.tonezen.app.ui.components.StorageGlyph
import com.tonezen.app.ui.components.SyncGlyph
import com.tonezen.app.ui.components.WarningGlyph
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenGreen
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenScreenBrush
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun ProfileScreen(
    padding: PaddingValues,
    viewModel: ProfileViewModel,
) {
    val state by viewModel.uiState.collectAsState()
    val online = state.sessionState == SessionState.AUTHENTICATED_ONLINE

    if (state.showSignOutConfirm) {
        SignOutConfirmDialog(
            onDismiss = { viewModel.setSignOutConfirmVisible(false) },
            onConfirm = {
                viewModel.setSignOutConfirmVisible(false)
                viewModel.logout()
            },
        )
    }
    if (state.showSyncDialog) {
        OfflineSyncDialog(
            onDismiss = { viewModel.setSyncDialogVisible(false) },
            onRetry = {
                viewModel.setSyncDialogVisible(false)
                viewModel.syncNow()
            },
        )
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(TonezenScreenBrush).padding(padding),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.profile_title), color = TonezenInk, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusChip(
                        label = if (online) stringResource(R.string.online) else stringResource(R.string.offline),
                        tone = if (online) TonezenGreen else TonezenAmber,
                    )
                    Box {
                        IconCircle(modifier = Modifier.clickable { viewModel.setOverflowMenuVisible(true) }) {
                            OverflowGlyph()
                        }
                        DropdownMenu(
                            expanded = state.showOverflowMenu,
                            onDismissRequest = { viewModel.setOverflowMenuVisible(false) },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.sign_out), color = TonezenInk) },
                                onClick = { viewModel.setSignOutConfirmVisible(true) },
                            )
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(TonezenSurfaceRaised),
                    contentAlignment = Alignment.Center,
                ) {
                    ProfileGlyph(tint = TonezenTeal, size = 30.dp)
                }
                Column {
                    Text(state.email.orEmpty(), color = TonezenInk, fontWeight = FontWeight.SemiBold)
                    Text(state.userId.orEmpty(), color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TonezenSurfaceRaised)
                    .border(1.dp, TonezenBorder, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(stringResource(R.string.sync_status_all_set), color = TonezenInk, fontWeight = FontWeight.SemiBold)
                Text(state.lastSyncLabel ?: stringResource(R.string.last_sync_today), color = TonezenMuted)
                if (state.pendingSyncCount > 0) {
                    StatusChip(label = stringResource(R.string.pending), tone = TonezenAmber)
                }
                OutlinedButton(onClick = viewModel::syncNow, enabled = !state.syncing) {
                    Text(stringResource(R.string.sync_now), color = TonezenTeal)
                }
            }
        }
        item {
            SettingsRow(
                title = stringResource(R.string.settings_account),
                subtitle = stringResource(R.string.email),
                icon = { ProfileGlyph(tint = TonezenInk) },
            )
            SettingsRow(
                title = stringResource(R.string.settings_sync),
                subtitle = stringResource(R.string.sync_now),
                icon = { SyncGlyph(tint = TonezenInk) },
            )
            SettingsRow(
                title = stringResource(R.string.settings_storage),
                subtitle = formatGb(state.storageUsedBytes),
                icon = { StorageGlyph(tint = TonezenInk) },
            )
            SettingsRow(
                title = stringResource(R.string.settings_privacy),
                subtitle = "",
                icon = { LockGlyph(tint = TonezenInk) },
            )
        }
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(TonezenSurfaceRaised)
                    .border(1.dp, TonezenAmber.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                WarningGlyph(tint = TonezenAmber)
                Text(stringResource(R.string.music_progress_local_warning), color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun SettingsRow(title: String, subtitle: String, icon: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(TonezenSurfaceRaised.copy(alpha = 0.6f))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            icon()
            Column {
                Text(title, color = TonezenInk, fontWeight = FontWeight.Medium)
                if (subtitle.isNotBlank()) {
                    Text(subtitle, color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        ChevronRightGlyph()
    }
}

private fun formatGb(bytes: Long): String = "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
