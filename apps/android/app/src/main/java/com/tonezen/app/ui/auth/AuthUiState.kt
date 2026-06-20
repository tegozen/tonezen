package com.tonezen.app.ui.auth

data class AuthUiState(
    val error: String? = null,
    val inviteCodeVerified: Boolean = false,
    val passwordRecoverySent: Boolean = false,
    val busy: Boolean = false,
)
