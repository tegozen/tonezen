package com.tonezen.app.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.testing.TestTags
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenMuted

@Composable
internal fun AuthSignInForm(
    mode: AuthFormMode,
    email: String,
    password: String,
    passwordVisible: Boolean,
    inviteCode: String,
    inviteCodeVerified: Boolean,
    signupEmail: String,
    signupName: String,
    signupPassword: String,
    signupConfirmPassword: String,
    signupPasswordVisible: Boolean,
    recoveryEmail: String,
    passwordRecoverySent: Boolean,
    error: String?,
    canSubmit: Boolean,
    canVerifyInvite: Boolean,
    canSignup: Boolean,
    canRecover: Boolean,
    onModeChange: (AuthFormMode) -> Unit,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onSubmit: () -> Unit,
    onInviteCodeChange: (String) -> Unit,
    onVerifyInviteCode: () -> Unit,
    onSignupEmailChange: (String) -> Unit,
    onSignupNameChange: (String) -> Unit,
    onSignupPasswordChange: (String) -> Unit,
    onSignupConfirmPasswordChange: (String) -> Unit,
    onToggleSignupPasswordVisible: () -> Unit,
    onSignup: () -> Unit,
    onRecoveryEmailChange: (String) -> Unit,
    onPasswordRecovery: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        when (mode) {
            AuthFormMode.Login -> AuthLoginFields(
                email = email,
                password = password,
                passwordVisible = passwordVisible,
                canSubmit = canSubmit,
                onEmailChange = onEmailChange,
                onPasswordChange = onPasswordChange,
                onTogglePasswordVisible = onTogglePasswordVisible,
                onSubmit = onSubmit,
                onModeChange = onModeChange,
            )
            AuthFormMode.Signup -> AuthSignupFields(
                inviteCode = inviteCode,
                inviteCodeVerified = inviteCodeVerified,
                signupEmail = signupEmail,
                signupName = signupName,
                signupPassword = signupPassword,
                signupConfirmPassword = signupConfirmPassword,
                signupPasswordVisible = signupPasswordVisible,
                canVerifyInvite = canVerifyInvite,
                canSignup = canSignup,
                onInviteCodeChange = onInviteCodeChange,
                onVerifyInviteCode = onVerifyInviteCode,
                onSignupEmailChange = onSignupEmailChange,
                onSignupNameChange = onSignupNameChange,
                onSignupPasswordChange = onSignupPasswordChange,
                onSignupConfirmPasswordChange = onSignupConfirmPasswordChange,
                onToggleSignupPasswordVisible = onToggleSignupPasswordVisible,
                onSignup = onSignup,
                onModeChange = onModeChange,
            )
            AuthFormMode.Recovery -> AuthRecoveryFields(
                recoveryEmail = recoveryEmail,
                passwordRecoverySent = passwordRecoverySent,
                canRecover = canRecover,
                onRecoveryEmailChange = onRecoveryEmailChange,
                onPasswordRecovery = onPasswordRecovery,
                onModeChange = onModeChange,
            )
        }
        error?.let {
            Text(
                resolveAuthError(it),
                color = TonezenError,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .testTag(TestTags.AUTH_ERROR),
            )
        }
        Text(
            "Офлайн-воспроизведение работает с загруженными файлами. Истёкшая сессия остаётся активной офлайн.",
            color = TonezenMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 10.dp, end = 10.dp),
        )
    }
}
