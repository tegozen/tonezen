package com.tonezen.app.ui.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.remote.AuthRepository
import com.tonezen.app.data.remote.SessionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AuthViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState: StateFlow<AuthUiState> = _uiState.asStateFlow()

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                val signedIn = authRepository.signInWithPassword(email, password)
                sessionRepository.saveSession(signedIn)
            } catch (_: Exception) {
                _uiState.update { it.copy(error = AUTH_LOGIN_FAILED_ERROR) }
            }
        }
    }

    fun verifyInviteCode(code: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null, inviteCodeVerified = false) }
            try {
                val verified = authRepository.verifyInviteCode(code)
                _uiState.update { it.copy(inviteCodeVerified = verified) }
            } catch (_: Exception) {
                _uiState.update { it.copy(error = AUTH_INVITE_CODE_INVALID_ERROR) }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    fun registerWithInvite(
        inviteCode: String,
        email: String,
        displayName: String,
        password: String,
        confirmPassword: String,
    ) {
        if (password != confirmPassword) {
            _uiState.update { it.copy(error = AUTH_PASSWORD_MISMATCH_ERROR) }
            return
        }
        if (password.length < MIN_PASSWORD_LENGTH) {
            _uiState.update { it.copy(error = AUTH_PASSWORD_TOO_SHORT_ERROR) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null) }
            try {
                authRepository.signUpWithInvite(
                    inviteCode = inviteCode,
                    email = email,
                    password = password,
                    displayName = displayName.ifBlank { null },
                )
                val signedIn = authRepository.signInWithPassword(email, password)
                sessionRepository.saveSession(signedIn)
            } catch (_: Exception) {
                _uiState.update { it.copy(error = AUTH_SIGNUP_FAILED_ERROR) }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    fun requestPasswordRecovery(email: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(busy = true, error = null, passwordRecoverySent = false) }
            try {
                authRepository.requestPasswordRecovery(email)
                _uiState.update { it.copy(passwordRecoverySent = true) }
            } catch (_: Exception) {
                _uiState.update { it.copy(error = AUTH_RECOVERY_FAILED_ERROR) }
            } finally {
                _uiState.update { it.copy(busy = false) }
            }
        }
    }

    companion object {
        private const val MIN_PASSWORD_LENGTH = 6
        const val AUTH_LOGIN_FAILED_ERROR = "__auth_login_failed__"
        const val AUTH_INVITE_CODE_INVALID_ERROR = "__auth_invite_code_invalid__"
        const val AUTH_SIGNUP_FAILED_ERROR = "__auth_signup_failed__"
        const val AUTH_PASSWORD_MISMATCH_ERROR = "__auth_password_mismatch__"
        const val AUTH_PASSWORD_TOO_SHORT_ERROR = "__auth_password_too_short__"
        const val AUTH_RECOVERY_FAILED_ERROR = "__auth_recovery_failed__"
    }
}
