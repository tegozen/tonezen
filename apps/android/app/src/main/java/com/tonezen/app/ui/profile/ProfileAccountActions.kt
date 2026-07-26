package com.tonezen.app.ui.profile

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Account-related actions for [ProfileViewModel] (profile save, password change, referral
 * code, avatar upload) and the offline/validation errors they report through `_uiState`.
 */
internal fun ProfileViewModel.saveProfile(displayName: String) {
    if (!networkMonitor.isOnline()) {
        _uiState.update { it.copy(profileError = ProfileViewModel.ACCOUNT_OFFLINE_ERROR) }
        return
    }
    viewModelScope.launch {
        _uiState.update { it.copy(profileSaving = true, profileError = null) }
        try {
            val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
                ?: throw IllegalStateException(ProfileViewModel.NOT_SIGNED_IN_ERROR)
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
            profileSyncRepository.mirrorSession(sessionRepository.loadSession() ?: session)
        } catch (_: Exception) {
            _uiState.update { it.copy(profileError = ProfileViewModel.PROFILE_UPDATE_FAILED_ERROR) }
        } finally {
            _uiState.update { it.copy(profileSaving = false) }
        }
    }
}

internal fun ProfileViewModel.changePassword(
    currentPassword: String,
    newPassword: String,
    confirmPassword: String,
) {
    if (!networkMonitor.isOnline()) {
        _uiState.update { it.copy(passwordError = ProfileViewModel.ACCOUNT_OFFLINE_ERROR) }
        return
    }
    if (currentPassword.isBlank()) {
        _uiState.update { it.copy(passwordError = "Введите текущий пароль") }
        return
    }
    if (newPassword != confirmPassword) {
        _uiState.update { it.copy(passwordError = ProfileViewModel.PASSWORD_MISMATCH_ERROR) }
        return
    }
    if (newPassword.length < ProfileViewModel.MIN_PASSWORD_LENGTH) {
        _uiState.update { it.copy(passwordError = ProfileViewModel.PASSWORD_TOO_SHORT_ERROR) }
        return
    }
    viewModelScope.launch {
        _uiState.update { it.copy(passwordSaving = true, passwordError = null) }
        try {
            val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
                ?: throw IllegalStateException(ProfileViewModel.NOT_SIGNED_IN_ERROR)
            authRepository.changePassword(
                accessToken = session.accessToken,
                currentPassword = currentPassword,
                newPassword = newPassword,
            )
            _uiState.update { it.copy(passwordFormNonce = it.passwordFormNonce + 1) }
        } catch (_: Exception) {
            _uiState.update { it.copy(passwordError = ProfileViewModel.PASSWORD_CHANGE_FAILED_ERROR) }
        } finally {
            _uiState.update { it.copy(passwordSaving = false) }
        }
    }
}

internal fun ProfileViewModel.loadReferralCode() {
    if (!networkMonitor.isOnline()) {
        _uiState.update { it.copy(referralCodeError = ProfileViewModel.ACCOUNT_OFFLINE_ERROR) }
        return
    }
    viewModelScope.launch {
        _uiState.update { it.copy(referralCodeError = null) }
        try {
            val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
                ?: throw IllegalStateException(ProfileViewModel.NOT_SIGNED_IN_ERROR)
            val code = authRepository.getReferralCode(session.accessToken)
            _uiState.update { it.copy(referralCode = code) }
        } catch (_: Exception) {
            _uiState.update { it.copy(referralCodeError = ProfileViewModel.REFERRAL_CODE_FAILED_ERROR) }
        }
    }
}

internal fun ProfileViewModel.uploadAvatar(jpegBytes: ByteArray) {
    if (!networkMonitor.isOnline()) {
        _uiState.update { it.copy(avatarUploadError = ProfileViewModel.ACCOUNT_OFFLINE_ERROR) }
        return
    }
    viewModelScope.launch {
        _uiState.update { it.copy(avatarUploading = true, avatarUploadError = null, profileError = null) }
        try {
            val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
                ?: throw IllegalStateException(ProfileViewModel.NOT_SIGNED_IN_ERROR)
            val avatarUrl = avatarRepository.uploadAvatar(
                accessToken = session.accessToken,
                userId = session.userId,
                jpegBytes = jpegBytes,
            )
            authRepository.updateUser(
                accessToken = session.accessToken,
                avatarUrl = avatarUrl,
            )
            val updatedSession = session.copy(avatarUrl = avatarUrl)
            sessionRepository.saveSession(updatedSession)
            profileSyncRepository.mirrorSession(updatedSession)
            _uiState.value.avatarCropUri?.let(::deleteCachedAvatarUri)
            _uiState.update {
                it.copy(
                    avatarUrl = avatarUrl,
                    avatarCropUri = null,
                    avatarUploadError = null,
                )
            }
        } catch (_: Exception) {
            _uiState.update { it.copy(avatarUploadError = ProfileViewModel.AVATAR_UPLOAD_FAILED_ERROR) }
        } finally {
            _uiState.update { it.copy(avatarUploading = false) }
        }
    }
}
