package com.tonezen.app.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.AuthRepository
import com.tonezen.app.data.remote.AvatarRepository
import com.tonezen.app.data.remote.ProfileSyncRepository
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.TrackDownloadQueueController
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Thin facade for the Profile feature. Account-related actions (profile save, password
 * change, referral code, avatar upload) live in [ProfileAccountActions] as extension
 * functions on this class so the public API stays a single `ProfileViewModel`.
 */
@HiltViewModel
class ProfileViewModel @Inject constructor(
    internal val sessionRepository: SessionRepository,
    internal val authRepository: AuthRepository,
    internal val avatarRepository: AvatarRepository,
    private val progressSyncRepository: ProgressSyncRepository,
    internal val profileSyncRepository: ProfileSyncRepository,
    private val catalogRepository: CatalogRepository,
    internal val networkMonitor: NetworkMonitor,
    private val playbackClient: PlaybackClient,
    private val localLibraryNotifier: LocalLibraryNotifier,
    private val downloadQueueController: TrackDownloadQueueController,
) : ViewModel() {
    private val syncTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
    private val memberSinceFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    internal val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            progressSyncRepository.lastSyncAtEpochMs.collectLatest { epochMs ->
                _uiState.update {
                    it.copy(lastSyncTime = epochMs?.let { value -> formatSyncTime(value) })
                }
            }
        }
        viewModelScope.launch {
            sessionRepository.session.collectLatest { session ->
                val resolvedSession = sessionRepository.enrichProfileMetadataIfMissing(session) ?: session
                _uiState.update {
                    it.copy(
                        sessionState = sessionRepository.resolveState(resolvedSession),
                        displayName = resolvedSession?.displayName,
                        email = resolvedSession?.email,
                        memberSinceLabel = formatMemberSince(resolvedSession?.memberSinceEpochMs),
                        avatarUrl = resolvedSession?.avatarUrl,
                    )
                }
                refreshStats()
            }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val stats = catalogRepository.getStorageStats()
            val pending = catalogRepository.getPendingSyncCount()
            _uiState.update {
                it.copy(
                    storageUsedBytes = stats.usedBytes,
                    storageTotalBytes = stats.totalBytes,
                    pendingSyncCount = pending,
                )
            }
        }
    }

    fun setSignOutConfirmVisible(visible: Boolean) {
        _uiState.update { it.copy(showSignOutConfirm = visible) }
    }

    fun setSyncDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showSyncDialog = visible) }
    }

    fun openSettingsScreen(action: ProfileSettingsAction) {
        _uiState.update {
            it.copy(
                activeSettingsScreen = action,
                profileError = if (action == ProfileSettingsAction.Account) null else it.profileError,
                passwordError = if (action == ProfileSettingsAction.Account) null else it.passwordError,
                referralCodeError = if (action == ProfileSettingsAction.Account) null else it.referralCodeError,
            )
        }
        if (action == ProfileSettingsAction.Account) {
            loadReferralCode()
        }
    }

    fun closeSettingsScreen() {
        _uiState.update { it.copy(activeSettingsScreen = null, showDeleteAllConfirm = false) }
    }

    fun setDeleteAllConfirmVisible(visible: Boolean) {
        _uiState.update { it.copy(showDeleteAllConfirm = visible) }
    }

    fun deleteAllDownloads() {
        viewModelScope.launch {
            try {
                downloadQueueController.cancelAllAwait()
                playbackClient.stopAndRelease()
                catalogRepository.deleteAllDownloads()
                localLibraryNotifier.notifyLocalLibraryChanged()
                refreshStats()
            } finally {
                _uiState.update { it.copy(showDeleteAllConfirm = false) }
            }
        }
    }

    fun onSettingsClick(action: ProfileSettingsAction) {
        openSettingsScreen(action)
    }

    fun onAvatarPicked(uri: Uri) {
        _uiState.update { it.copy(avatarCropUri = uri, profileError = null, avatarUploadError = null) }
    }

    fun dismissAvatarCrop() {
        _uiState.value.avatarCropUri?.let(::deleteCachedAvatarUri)
        _uiState.update { it.copy(avatarCropUri = null, avatarUploadError = null) }
    }

    fun syncNow() {
        if (!networkMonitor.isOnline()) {
            _uiState.update { it.copy(showSyncDialog = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(syncing = true) }
            try {
                val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
                if (session != null) {
                    progressSyncRepository.pullAll(session.accessToken)
                    progressSyncRepository.flushPending(session.accessToken)
                }
                refreshStats()
            } catch (_: Exception) {
                // Manual sync is best-effort; offline/local state is unchanged.
            } finally {
                _uiState.update { it.copy(syncing = false) }
            }
        }
    }

    fun logout() {
        progressSyncRepository.stop()
        profileSyncRepository.stop()
        playbackClient.stopAndRelease()
        sessionRepository.clearSession()
    }

    private fun formatSyncTime(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(syncTimeFormatter)

    private fun formatMemberSince(epochMs: Long?): String? {
        if (epochMs == null) return null
        return Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .format(memberSinceFormatter)
    }

    companion object {
        const val ACCOUNT_OFFLINE_ERROR = "__account_offline__"
        const val PASSWORD_MISMATCH_ERROR = "__password_mismatch__"
        const val PASSWORD_TOO_SHORT_ERROR = "__password_too_short__"
        const val PROFILE_UPDATE_FAILED_ERROR = "__profile_update_failed__"
        const val PASSWORD_CHANGE_FAILED_ERROR = "__password_change_failed__"
        const val AVATAR_UPLOAD_FAILED_ERROR = "__avatar_upload_failed__"
        const val REFERRAL_CODE_FAILED_ERROR = "__referral_code_failed__"
        const val NOT_SIGNED_IN_ERROR = "__not_signed_in__"
        internal const val MIN_PASSWORD_LENGTH = 12
    }
}
