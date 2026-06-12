package com.tonezen.app.ui.profile

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.AuthRepository
import com.tonezen.app.data.remote.AvatarRepository
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.playback.PlaybackClient
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

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val avatarRepository: AvatarRepository,
    private val progressSyncRepository: ProgressSyncRepository,
    private val catalogRepository: CatalogRepository,
    private val networkMonitor: NetworkMonitor,
    private val playbackClient: PlaybackClient,
    private val localLibraryNotifier: LocalLibraryNotifier,
) : ViewModel() {
    private val syncTimeFormatter = DateTimeFormatter.ofPattern("H:mm")
    private val memberSinceFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    private val _uiState = MutableStateFlow(ProfileUiState())
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
            )
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
                catalogRepository.deleteAllDownloads()
                localLibraryNotifier.notifyLocalLibraryChanged()
                refreshStats()
            } finally {
                _uiState.update { it.copy(showDeleteAllConfirm = false) }
            }
        }
    }

    fun saveProfile(displayName: String) {
        if (!networkMonitor.isOnline()) {
            _uiState.update { it.copy(profileError = ACCOUNT_OFFLINE_ERROR) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(profileSaving = true, profileError = null) }
            try {
                val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
                    ?: throw IllegalStateException(NOT_SIGNED_IN_ERROR)
                if (displayName == session.displayName) return@launch
                val updated = authRepository.updateUser(
                    accessToken = session.accessToken,
                    displayName = displayName,
                )
                sessionRepository.saveSession(
                    session.copy(
                        displayName = updated.displayName,
                        memberSinceEpochMs = session.memberSinceEpochMs ?: updated.memberSinceEpochMs,
                        avatarUrl = session.avatarUrl ?: updated.avatarUrl,
                    ),
                )
            } catch (e: Exception) {
                _uiState.update { it.copy(profileError = e.message) }
            } finally {
                _uiState.update { it.copy(profileSaving = false) }
            }
        }
    }

    fun changePassword(newPassword: String, confirmPassword: String) {
        if (!networkMonitor.isOnline()) {
            _uiState.update { it.copy(passwordError = ACCOUNT_OFFLINE_ERROR) }
            return
        }
        if (newPassword != confirmPassword) {
            _uiState.update { it.copy(passwordError = PASSWORD_MISMATCH_ERROR) }
            return
        }
        if (newPassword.length < MIN_PASSWORD_LENGTH) {
            _uiState.update { it.copy(passwordError = PASSWORD_TOO_SHORT_ERROR) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(passwordSaving = true, passwordError = null) }
            try {
                val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
                    ?: throw IllegalStateException(NOT_SIGNED_IN_ERROR)
                authRepository.updateUser(
                    accessToken = session.accessToken,
                    newPassword = newPassword,
                )
                _uiState.update { it.copy(passwordFormNonce = it.passwordFormNonce + 1) }
            } catch (e: Exception) {
                _uiState.update { it.copy(passwordError = e.message) }
            } finally {
                _uiState.update { it.copy(passwordSaving = false) }
            }
        }
    }

    fun onSettingsClick(action: ProfileSettingsAction) {
        openSettingsScreen(action)
    }

    fun onAvatarPicked(uri: Uri) {
        _uiState.update { it.copy(avatarCropUri = uri, profileError = null) }
    }

    fun dismissAvatarCrop() {
        _uiState.update { it.copy(avatarCropUri = null) }
    }

    fun uploadAvatar(jpegBytes: ByteArray) {
        if (!networkMonitor.isOnline()) {
            _uiState.update { it.copy(profileError = ACCOUNT_OFFLINE_ERROR) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(avatarUploading = true, profileError = null) }
            try {
                val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
                    ?: throw IllegalStateException(NOT_SIGNED_IN_ERROR)
                val avatarUrl = avatarRepository.uploadAvatar(
                    accessToken = session.accessToken,
                    userId = session.userId,
                    jpegBytes = jpegBytes,
                )
                authRepository.updateUser(
                    accessToken = session.accessToken,
                    avatarUrl = avatarUrl,
                )
                sessionRepository.saveSession(session.copy(avatarUrl = avatarUrl))
                _uiState.update { it.copy(avatarCropUri = null) }
            } catch (e: Exception) {
                _uiState.update { it.copy(profileError = e.message) }
            } finally {
                _uiState.update { it.copy(avatarUploading = false) }
            }
        }
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
            } finally {
                _uiState.update { it.copy(syncing = false) }
            }
        }
    }

    private fun formatSyncTime(epochMs: Long): String =
        Instant.ofEpochMilli(epochMs)
            .atZone(ZoneId.systemDefault())
            .toLocalTime()
            .format(syncTimeFormatter)

    fun logout() {
        progressSyncRepository.stop()
        playbackClient.stopAndRelease()
        sessionRepository.clearSession()
    }

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
        const val NOT_SIGNED_IN_ERROR = "__not_signed_in__"
        private const val MIN_PASSWORD_LENGTH = 6
    }
}
