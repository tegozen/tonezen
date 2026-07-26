package com.tonezen.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.theme.TonezenScreenBrush

@Composable
fun AuthScreen(
    padding: PaddingValues,
    onLogin: (String, String) -> Unit,
    onVerifyInviteCode: (String) -> Unit = {},
    onSignup: (String, String, String, String, String) -> Unit = { _, _, _, _, _ -> },
    onPasswordRecovery: (String) -> Unit = {},
    inviteCodeVerified: Boolean = false,
    passwordRecoverySent: Boolean = false,
    error: String?,
) {
    var mode by remember { mutableStateOf(AuthFormMode.Login) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var inviteCode by remember { mutableStateOf("") }
    var signupEmail by remember { mutableStateOf("") }
    var signupName by remember { mutableStateOf("") }
    var signupPassword by remember { mutableStateOf("") }
    var signupConfirmPassword by remember { mutableStateOf("") }
    var signupPasswordVisible by remember { mutableStateOf(false) }
    var recoveryEmail by remember { mutableStateOf("") }
    val canSubmit = email.isNotBlank() && password.isNotBlank()
    val canVerifyInvite = inviteCode.isNotBlank()
    val canSignup = inviteCodeVerified &&
        signupEmail.isNotBlank() &&
        signupName.isNotBlank() &&
        signupPassword.length >= 6 &&
        signupPassword == signupConfirmPassword
    val canRecover = recoveryEmail.isNotBlank()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(TonezenScreenBrush)
            .padding(padding),
    ) {
        AuthStarField(modifier = Modifier.fillMaxSize())
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 24.dp, top = 44.dp, end = 24.dp, bottom = 30.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            item {
                AuthIntroPanel()
            }
            item {
                AuthSignInForm(
                    mode = mode,
                    email = email,
                    password = password,
                    passwordVisible = passwordVisible,
                    inviteCode = inviteCode,
                    inviteCodeVerified = inviteCodeVerified,
                    signupEmail = signupEmail,
                    signupName = signupName,
                    signupPassword = signupPassword,
                    signupConfirmPassword = signupConfirmPassword,
                    signupPasswordVisible = signupPasswordVisible,
                    recoveryEmail = recoveryEmail,
                    passwordRecoverySent = passwordRecoverySent,
                    error = error,
                    canSubmit = canSubmit,
                    canVerifyInvite = canVerifyInvite,
                    canSignup = canSignup,
                    canRecover = canRecover,
                    onModeChange = { mode = it },
                    onEmailChange = { email = it },
                    onPasswordChange = { password = it },
                    onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                    onSubmit = { onLogin(email.trim(), password) },
                    onInviteCodeChange = { inviteCode = it },
                    onVerifyInviteCode = { onVerifyInviteCode(inviteCode.trim()) },
                    onSignupEmailChange = { signupEmail = it },
                    onSignupNameChange = { signupName = it },
                    onSignupPasswordChange = { signupPassword = it },
                    onSignupConfirmPasswordChange = { signupConfirmPassword = it },
                    onToggleSignupPasswordVisible = { signupPasswordVisible = !signupPasswordVisible },
                    onSignup = {
                        onSignup(
                            inviteCode.trim(),
                            signupEmail.trim(),
                            signupName.trim(),
                            signupPassword,
                            signupConfirmPassword,
                        )
                    },
                    onRecoveryEmailChange = { recoveryEmail = it },
                    onPasswordRecovery = { onPasswordRecovery(recoveryEmail.trim()) },
                )
            }
        }
    }
}

internal enum class AuthFormMode {
    Login,
    Signup,
    Recovery,
}
