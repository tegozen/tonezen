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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import com.tonezen.app.ui.theme.TonezenFaint
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.ui.components.CheckCircleGlyph
import com.tonezen.app.ui.components.ChevronRightGlyph
import com.tonezen.app.ui.components.OfflineSyncDialog
import com.tonezen.app.ui.components.ProfileGlyph
import com.tonezen.app.ui.components.SignOutConfirmDialog
import com.tonezen.app.ui.components.StatusChip
import com.tonezen.app.ui.components.StorageGlyph
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.components.TonezenTitleChromeBar
import com.tonezen.app.ui.theme.TonezenPageChromeScrollPadding
import com.tonezen.app.ui.theme.TonezenProfileBottomExtraScrollPadding
import com.tonezen.app.ui.theme.tonezenBottomChromeScrollPadding
import com.tonezen.app.ui.theme.tonezenScreenContentPadding
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenGreen
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
internal fun ProfileScreen(
    padding: PaddingValues,
    hazeState: HazeState,
    viewModel: ProfileViewModel,
    showMiniPlayer: Boolean = false,
) {
    val state by viewModel.uiState.collectAsState()
    val bottomScrollPadding = tonezenBottomChromeScrollPadding(
        showMiniPlayer = showMiniPlayer,
        showBottomNav = true,
    )

    BackHandler(
        enabled = state.showSignOutConfirm ||
            state.showSyncDialog ||
            state.showDeleteAllConfirm ||
            state.activeSettingsScreen != null,
    ) {
        when {
            state.showDeleteAllConfirm -> viewModel.setDeleteAllConfirmVisible(false)
            state.showSignOutConfirm -> viewModel.setSignOutConfirmVisible(false)
            state.showSyncDialog -> viewModel.setSyncDialogVisible(false)
            state.activeSettingsScreen != null -> viewModel.closeSettingsScreen()
        }
    }

    SignOutConfirmDialog(
        visible = state.showSignOutConfirm,
        hazeState = hazeState,
        onDismiss = { viewModel.setSignOutConfirmVisible(false) },
        onConfirm = {
            viewModel.setSignOutConfirmVisible(false)
            viewModel.logout()
        },
    )
    OfflineSyncDialog(
        visible = state.showSyncDialog,
        hazeState = hazeState,
        onDismiss = { viewModel.setSyncDialogVisible(false) },
        onRetry = {
            viewModel.setSyncDialogVisible(false)
            viewModel.syncNow()
        },
    )
    when (state.activeSettingsScreen) {
        ProfileSettingsAction.Account -> AccountSettingsScreen(
            padding = padding,
            hazeState = hazeState,
            bottomScrollPadding = bottomScrollPadding,
            displayName = state.displayName.orEmpty(),
            email = state.email.orEmpty(),
            profileSaving = state.profileSaving,
            passwordSaving = state.passwordSaving,
            profileError = resolveAccountError(state.profileError),
            passwordError = resolveAccountError(state.passwordError),
            passwordFormNonce = state.passwordFormNonce,
            onBack = viewModel::closeSettingsScreen,
            onSaveProfile = viewModel::saveProfile,
            onChangePassword = viewModel::changePassword,
        )
        ProfileSettingsAction.Storage -> StorageSettingsScreen(
            padding = padding,
            hazeState = hazeState,
            bottomScrollPadding = bottomScrollPadding,
            usedBytes = state.storageUsedBytes,
            totalBytes = state.storageTotalBytes,
            showDeleteAllConfirm = state.showDeleteAllConfirm,
            onBack = viewModel::closeSettingsScreen,
            onDeleteAllClick = { viewModel.setDeleteAllConfirmVisible(true) },
            onDismissDeleteAllConfirm = { viewModel.setDeleteAllConfirmVisible(false) },
            onConfirmDeleteAll = viewModel::deleteAllDownloads,
        )
        null -> ProfileScreenContent(
            padding = padding,
            hazeState = hazeState,
            state = state,
            showMiniPlayer = showMiniPlayer,
            onSignOutClick = { viewModel.setSignOutConfirmVisible(true) },
            onAccountClick = { viewModel.onSettingsClick(ProfileSettingsAction.Account) },
            onSettingsClick = viewModel::onSettingsClick,
        )
    }
}

@Composable
internal fun ProfileScreenContent(
    padding: PaddingValues,
    hazeState: HazeState,
    state: ProfileUiState,
    showMiniPlayer: Boolean = false,
    onSignOutClick: () -> Unit,
    onAccountClick: () -> Unit,
    onSettingsClick: (ProfileSettingsAction) -> Unit,
) {
    val online = state.sessionState == SessionState.AUTHENTICATED_ONLINE
    val settingsItems = listOf(
        SettingsItem(
            action = ProfileSettingsAction.Storage,
            titleRes = R.string.settings_storage,
            subtitleRes = R.string.settings_storage_subtitle,
            icon = { StorageGlyph(tint = TonezenInk) },
        ),
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TonezenSurface)
            .padding(padding),
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState),
            contentPadding = tonezenScreenContentPadding(
                top = TonezenPageChromeScrollPadding,
                bottom = tonezenBottomChromeScrollPadding(
                    showMiniPlayer = showMiniPlayer,
                    showBottomNav = true,
                    extraBottom = TonezenProfileBottomExtraScrollPadding,
                ),
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ProfileUserCard(
                    displayName = state.displayName.orEmpty(),
                    email = state.email,
                    memberSinceLabel = state.memberSinceLabel,
                    avatarUrl = state.avatarUrl,
                    onClick = onAccountClick,
                )
            }
            item {
                Column {
                    ProfileSectionLabel(stringResource(R.string.profile_sync_status_section))
                    SyncStatusCard(
                        lastSyncTime = state.lastSyncTime,
                        pendingSyncCount = state.pendingSyncCount,
                    )
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProfileSectionLabel(stringResource(R.string.profile_settings_section))
                    SettingsGroup(
                        items = settingsItems,
                        onItemClick = onSettingsClick,
                    )
                    SignOutCard(onClick = onSignOutClick)
                }
            }
        }
        TonezenTitleChromeBar(
            modifier = Modifier.align(Alignment.TopCenter),
            hazeState = hazeState,
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.profile_title),
                    color = TonezenInk,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                StatusChip(
                    label = if (online) stringResource(R.string.online) else stringResource(R.string.offline),
                    tone = if (online) TonezenGreen else TonezenAmber,
                )
            }
        }
    }
}

@Composable
private fun ProfileUserCard(
    displayName: String,
    email: String?,
    memberSinceLabel: String?,
    avatarUrl: String?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .background(TonezenSurfaceRaised)
            .border(1.dp, TonezenBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .border(2.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                .padding(2.dp)
                .clip(CircleShape)
                .background(TonezenSurfaceRaised),
            contentAlignment = Alignment.Center,
        ) {
            if (!avatarUrl.isNullOrBlank()) {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = null,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                ProfileGlyph(tint = TonezenTeal, size = 28.dp)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                displayName,
                color = TonezenInk,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyLarge,
            )
            email?.takeIf { it.isNotBlank() }?.let { value ->
                Text(value, color = TonezenMuted, style = MaterialTheme.typography.bodySmall)
            }
            memberSinceLabel?.let { value ->
                Text(
                    stringResource(R.string.profile_member_since, value),
                    color = TonezenFaint,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun ProfileSectionLabel(label: String) {
    Text(
        label.uppercase(),
        color = TonezenMuted,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SyncStatusCard(
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
                stringResource(R.string.sync_status_all_set),
                color = TonezenInk,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                if (lastSyncTime != null) {
                    stringResource(R.string.last_sync_today_at, lastSyncTime)
                } else {
                    stringResource(R.string.last_sync_never)
                },
                color = TonezenMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            if (pendingSyncCount > 0) {
                StatusChip(label = stringResource(R.string.pending), tone = TonezenAmber)
            }
        }
    }
}

@Composable
private fun SignOutCard(onClick: () -> Unit) {
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
            stringResource(R.string.sign_out),
            color = TonezenError,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun SettingsGroup(
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
                title = stringResource(item.titleRes),
                subtitle = stringResource(item.subtitleRes),
                icon = item.icon,
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
        ChevronRightGlyph()
    }
}

private data class SettingsItem(
    val action: ProfileSettingsAction,
    val titleRes: Int,
    val subtitleRes: Int,
    val icon: @Composable () -> Unit,
)

