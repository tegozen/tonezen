package com.tonezen.app.ui.profile

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonezen.app.data.nearby.PeerNearbyPermissions
import com.tonezen.app.ui.theme.tonezenBottomChromeScrollPadding
import dev.chrisbanes.haze.HazeState

@Composable
internal fun ProfileScreen(
    padding: PaddingValues,
    hazeState: HazeState,
    viewModel: ProfileViewModel,
    peerViewModel: PeerProgressViewModel = hiltViewModel(),
    showMiniPlayer: Boolean = false,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val peerState by peerViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingPeerAction by remember { mutableStateOf<PeerAction?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        val granted = grants.values.all { it } ||
            PeerNearbyPermissions.missing(context).isEmpty()
        when (pendingPeerAction) {
            PeerAction.Accept -> peerViewModel.onAcceptClick(granted)
            PeerAction.Send -> peerViewModel.onSendClick(granted)
            null -> Unit
        }
        pendingPeerAction = null
    }

    fun startPeer(action: PeerAction) {
        val missing = PeerNearbyPermissions.missing(context)
        if (missing.isEmpty()) {
            when (action) {
                PeerAction.Accept -> peerViewModel.onAcceptClick(true)
                PeerAction.Send -> peerViewModel.onSendClick(true)
            }
        } else {
            pendingPeerAction = action
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    val bottomScrollPadding = tonezenBottomChromeScrollPadding(
        showMiniPlayer = showMiniPlayer,
        showBottomNav = true,
    )

    val peerBusy = peerState.mode != PeerSessionMode.Idle ||
        peerState.pendingOffer != null ||
        peerState.conflictPrompt != null ||
        peerState.alertTitle != null

    BackHandler(
        enabled = state.showSignOutConfirm ||
            state.showSyncDialog ||
            state.showDeleteAllConfirm ||
            state.activeSettingsScreen != null ||
            peerBusy,
    ) {
        when {
            peerState.alertTitle != null -> peerViewModel.dismissAlert()
            peerState.conflictPrompt != null -> peerViewModel.chooseConflictLocal()
            peerState.pendingOffer != null -> peerViewModel.rejectIncomingOffer()
            peerState.mode == PeerSessionMode.Accepting -> peerViewModel.dismissAccepting()
            peerState.mode == PeerSessionMode.DiscoveringDevices ||
                peerState.mode == PeerSessionMode.PickingCycle ||
                peerState.mode == PeerSessionMode.Sending -> peerViewModel.dismissSending()
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

    PeerAcceptWaitingDialog(
        visible = peerState.mode == PeerSessionMode.Accepting && peerState.pendingOffer == null,
        statusMessage = peerState.statusMessage,
        hazeState = hazeState,
        onDismiss = peerViewModel::dismissAccepting,
    )
    PeerIncomingOfferDialog(
        visible = peerState.pendingOffer != null,
        deviceLabel = peerState.pendingOffer?.offer?.deviceLabel.orEmpty(),
        cycleTitle = peerState.pendingOffer?.offer?.cycleTitle.orEmpty(),
        hazeState = hazeState,
        onAccept = peerViewModel::confirmIncomingOffer,
        onReject = peerViewModel::rejectIncomingOffer,
    )
    PeerDevicePickerSheet(
        visible = peerState.mode == PeerSessionMode.DiscoveringDevices,
        devices = peerState.devices,
        statusMessage = peerState.statusMessage,
        hazeState = hazeState,
        onDismiss = peerViewModel::dismissSending,
        onDeviceClick = peerViewModel::onDeviceSelected,
    )
    PeerCyclePickerSheet(
        visible = peerState.mode == PeerSessionMode.PickingCycle,
        cycles = peerState.cycles,
        hazeState = hazeState,
        onDismiss = peerViewModel::dismissSending,
        onCycleClick = peerViewModel::onCycleSelected,
    )
    PeerAcceptWaitingDialog(
        visible = peerState.mode == PeerSessionMode.Sending,
        statusMessage = peerState.statusMessage ?: "Отправка…",
        hazeState = hazeState,
        onDismiss = peerViewModel::dismissSending,
        title = "Отправка",
    )
    PeerConflictDialog(
        visible = peerState.conflictPrompt != null,
        cycleTitle = peerState.conflictPrompt?.cycleTitle.orEmpty(),
        hazeState = hazeState,
        onChooseLocal = peerViewModel::chooseConflictLocal,
        onChoosePeer = peerViewModel::chooseConflictPeer,
    )
    PeerAlertDialog(
        visible = peerState.alertTitle != null,
        title = peerState.alertTitle.orEmpty(),
        message = peerState.alertMessage.orEmpty(),
        hazeState = hazeState,
        onDismiss = peerViewModel::dismissAlert,
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
            onPeerAcceptClick = { startPeer(PeerAction.Accept) },
            onPeerSendClick = { startPeer(PeerAction.Send) },
        )
    }
}

private enum class PeerAction { Accept, Send }
