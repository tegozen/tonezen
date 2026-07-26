package com.tonezen.app.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonezen.app.ui.theme.tonezenBottomChromeScrollPadding
import dev.chrisbanes.haze.HazeState

@Composable
internal fun ProfileScreen(
    padding: PaddingValues,
    hazeState: HazeState,
    viewModel: ProfileViewModel,
    showMiniPlayer: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
    when {
        state.activeSettingsScreen == ProfileSettingsAction.Account -> AccountSettingsScreen(
            padding = padding,
            hazeState = hazeState,
            bottomScrollPadding = bottomScrollPadding,
            displayName = state.displayName.orEmpty(),
            email = state.email.orEmpty(),
            avatarUrl = state.avatarUrl,
            avatarUploading = state.avatarUploading,
            profileSaving = state.profileSaving,
            passwordSaving = state.passwordSaving,
            profileError = resolveAccountError(state.profileError),
            passwordError = resolveAccountError(state.passwordError),
            referralCode = state.referralCode,
            referralCodeError = resolveAccountError(state.referralCodeError),
            passwordFormNonce = state.passwordFormNonce,
            onBack = viewModel::closeSettingsScreen,
            onSaveProfile = viewModel::saveProfile,
            onChangePassword = viewModel::changePassword,
            onAvatarPicked = viewModel::onAvatarPicked,
        )
        state.activeSettingsScreen == ProfileSettingsAction.Storage -> StorageSettingsScreen(
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
        else -> ProfileScreenContent(
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
