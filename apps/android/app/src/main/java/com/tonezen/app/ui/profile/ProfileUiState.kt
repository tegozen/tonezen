package com.tonezen.app.ui.profile

import android.net.Uri
import com.tonezen.app.domain.model.SessionState

data class ProfileUiState(
    val sessionState: SessionState = SessionState.UNAUTHENTICATED,
    val displayName: String? = null,
    val email: String? = null,
    val memberSinceLabel: String? = null,
    val avatarUrl: String? = null,
    val pendingSyncCount: Int = 0,
    val storageUsedBytes: Long = 0L,
    val storageTotalBytes: Long? = null,
    val showSignOutConfirm: Boolean = false,
    val showSyncDialog: Boolean = false,
    val activeSettingsScreen: ProfileSettingsAction? = null,
    val profileSaving: Boolean = false,
    val passwordSaving: Boolean = false,
    val profileError: String? = null,
    val passwordError: String? = null,
    val passwordFormNonce: Int = 0,
    val avatarCropUri: Uri? = null,
    val avatarUploading: Boolean = false,
    val avatarUploadError: String? = null,
    val syncing: Boolean = false,
    val lastSyncTime: String? = null,
    val showDeleteAllConfirm: Boolean = false,
)
