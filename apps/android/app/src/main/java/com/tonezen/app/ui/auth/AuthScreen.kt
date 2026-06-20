package com.tonezen.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.ui.testing.TestTags
import com.tonezen.app.ui.components.EyeGlyph
import com.tonezen.app.ui.components.EyeOffGlyph
import com.tonezen.app.ui.components.LockGlyph
import com.tonezen.app.ui.components.MailGlyph
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenScreenBrush
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.TonezenTealStrong

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

private enum class AuthFormMode {
    Login,
    Signup,
    Recovery,
}

@Composable
private fun AuthSignInForm(
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
            AuthFormMode.Login -> {
                TonezenAuthField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = stringResource(R.string.email),
                    keyboardType = KeyboardType.Email,
                    icon = AuthFieldIcon.Email,
                    modifier = Modifier.testTag(TestTags.AUTH_EMAIL),
                )
                TonezenAuthField(
                    value = password,
                    onValueChange = onPasswordChange,
                    label = stringResource(R.string.password),
                    keyboardType = KeyboardType.Password,
                    icon = AuthFieldIcon.Password,
                    hidden = !passwordVisible,
                    showPasswordToggle = true,
                    passwordVisible = passwordVisible,
                    onTogglePasswordVisible = onTogglePasswordVisible,
                    modifier = Modifier.testTag(TestTags.AUTH_PASSWORD),
                )
                AuthPrimaryButton(
                    text = stringResource(R.string.sign_in),
                    enabled = canSubmit,
                    onClick = onSubmit,
                    modifier = Modifier.testTag(TestTags.AUTH_SIGN_IN),
                )
                AuthModeActions(
                    primaryText = stringResource(R.string.forgot_password),
                    primaryTag = TestTags.AUTH_SHOW_RECOVERY,
                    onPrimary = { onModeChange(AuthFormMode.Recovery) },
                    secondaryText = stringResource(R.string.no_account_yet),
                    secondaryTag = TestTags.AUTH_SHOW_SIGN_UP,
                    onSecondary = { onModeChange(AuthFormMode.Signup) },
                )
            }
            AuthFormMode.Signup -> {
                TonezenAuthField(
                    value = inviteCode,
                    onValueChange = onInviteCodeChange,
                    label = stringResource(R.string.invite_code),
                    keyboardType = KeyboardType.Text,
                    icon = AuthFieldIcon.Password,
                    modifier = Modifier.testTag(TestTags.AUTH_INVITE_CODE),
                )
                if (!inviteCodeVerified) {
                    AuthPrimaryButton(
                        text = stringResource(R.string.check_invite_code),
                        enabled = canVerifyInvite,
                        onClick = onVerifyInviteCode,
                        modifier = Modifier.testTag(TestTags.AUTH_VERIFY_INVITE),
                    )
                } else {
                    TonezenAuthField(
                        value = signupEmail,
                        onValueChange = onSignupEmailChange,
                        label = stringResource(R.string.email),
                        keyboardType = KeyboardType.Email,
                        icon = AuthFieldIcon.Email,
                        modifier = Modifier.testTag(TestTags.AUTH_SIGNUP_EMAIL),
                    )
                    TonezenAuthField(
                        value = signupName,
                        onValueChange = onSignupNameChange,
                        label = stringResource(R.string.auth_display_name),
                        keyboardType = KeyboardType.Text,
                        icon = AuthFieldIcon.Email,
                        modifier = Modifier.testTag(TestTags.AUTH_SIGNUP_NAME),
                    )
                    TonezenAuthField(
                        value = signupPassword,
                        onValueChange = onSignupPasswordChange,
                        label = stringResource(R.string.password),
                        keyboardType = KeyboardType.Password,
                        icon = AuthFieldIcon.Password,
                        hidden = !signupPasswordVisible,
                        showPasswordToggle = true,
                        passwordVisible = signupPasswordVisible,
                        onTogglePasswordVisible = onToggleSignupPasswordVisible,
                        modifier = Modifier.testTag(TestTags.AUTH_SIGNUP_PASSWORD),
                    )
                    TonezenAuthField(
                        value = signupConfirmPassword,
                        onValueChange = onSignupConfirmPasswordChange,
                        label = stringResource(R.string.confirm_password),
                        keyboardType = KeyboardType.Password,
                        icon = AuthFieldIcon.Password,
                        hidden = !signupPasswordVisible,
                        modifier = Modifier.testTag(TestTags.AUTH_SIGNUP_CONFIRM),
                    )
                    AuthPrimaryButton(
                        text = stringResource(R.string.create_account),
                        enabled = canSignup,
                        onClick = onSignup,
                        modifier = Modifier.testTag(TestTags.AUTH_SIGN_UP),
                    )
                }
                AuthTextButton(
                    text = stringResource(R.string.already_have_account),
                    onClick = { onModeChange(AuthFormMode.Login) },
                )
            }
            AuthFormMode.Recovery -> {
                Text(
                    stringResource(R.string.auth_recovery_title),
                    color = TonezenInk,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    stringResource(R.string.auth_recovery_body),
                    color = TonezenMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                TonezenAuthField(
                    value = recoveryEmail,
                    onValueChange = onRecoveryEmailChange,
                    label = stringResource(R.string.email),
                    keyboardType = KeyboardType.Email,
                    icon = AuthFieldIcon.Email,
                    modifier = Modifier.testTag(TestTags.AUTH_RECOVERY_EMAIL),
                )
                AuthPrimaryButton(
                    text = stringResource(R.string.auth_recovery_submit),
                    enabled = canRecover,
                    onClick = onPasswordRecovery,
                    modifier = Modifier.testTag(TestTags.AUTH_RECOVERY_SUBMIT),
                )
                if (passwordRecoverySent) {
                    Text(
                        stringResource(R.string.auth_recovery_sent),
                        color = TonezenTeal,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                AuthTextButton(
                    text = stringResource(R.string.back_to_sign_in),
                    onClick = { onModeChange(AuthFormMode.Login) },
                )
            }
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
            stringResource(R.string.offline_playback_note),
            color = TonezenMuted,
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp, start = 10.dp, end = 10.dp),
        )
    }
}

@Composable
private fun AuthPrimaryButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val gradient = Brush.verticalGradient(
        colors = if (enabled) {
            listOf(TonezenTeal, TonezenTealStrong)
        } else {
            listOf(TonezenTeal.copy(alpha = 0.45f), TonezenTealStrong.copy(alpha = 0.45f))
        },
    )
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(gradient)
            .then(modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = TonezenAppBg.copy(alpha = if (enabled) 1f else 0.72f),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AuthModeActions(
    primaryText: String,
    primaryTag: String,
    onPrimary: () -> Unit,
    secondaryText: String,
    secondaryTag: String,
    onSecondary: () -> Unit,
) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AuthTextButton(text = primaryText, onClick = onPrimary, modifier = Modifier.testTag(primaryTag))
        AuthTextButton(text = secondaryText, onClick = onSecondary, modifier = Modifier.testTag(secondaryTag))
    }
}

@Composable
private fun AuthTextButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text,
        color = TonezenTeal,
        style = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .padding(horizontal = 2.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
    )
}

private enum class AuthFieldIcon {
    Email,
    Password,
}

@Composable
private fun TonezenAuthField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    icon: AuthFieldIcon,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
    showPasswordToggle: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePasswordVisible: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label) },
        leadingIcon = { AuthFieldGlyph(icon) },
        trailingIcon = if (showPasswordToggle) {
            {
                IconButton(onClick = { onTogglePasswordVisible?.invoke() }) {
                    if (passwordVisible) {
                        EyeOffGlyph(tint = TonezenMuted, size = 19.dp)
                    } else {
                        EyeGlyph(tint = TonezenMuted, size = 19.dp)
                    }
                }
            }
        } else {
            null
        },
        modifier = modifier
            .fillMaxWidth()
            .height(58.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TonezenInk,
            unfocusedTextColor = TonezenInk,
            focusedContainerColor = TonezenSurface.copy(alpha = 0.72f),
            unfocusedContainerColor = TonezenSurface.copy(alpha = 0.56f),
            focusedBorderColor = TonezenTeal,
            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
            focusedPlaceholderColor = TonezenTeal.copy(alpha = 0.92f),
            unfocusedPlaceholderColor = TonezenMuted,
            cursorColor = TonezenTeal,
        ),
    )
}

@Composable
internal fun resolveAuthError(error: String): String = when (error) {
    AuthViewModel.AUTH_LOGIN_FAILED_ERROR -> stringResource(R.string.auth_login_failed)
    AuthViewModel.AUTH_INVITE_CODE_INVALID_ERROR -> stringResource(R.string.auth_invite_code_invalid)
    AuthViewModel.AUTH_SIGNUP_FAILED_ERROR -> stringResource(R.string.auth_signup_failed)
    AuthViewModel.AUTH_PASSWORD_MISMATCH_ERROR -> stringResource(R.string.auth_password_mismatch)
    AuthViewModel.AUTH_PASSWORD_TOO_SHORT_ERROR -> stringResource(R.string.auth_password_too_short)
    AuthViewModel.AUTH_RECOVERY_FAILED_ERROR -> stringResource(R.string.auth_recovery_failed)
    else -> stringResource(R.string.auth_login_failed)
}

@Composable
private fun AuthFieldGlyph(icon: AuthFieldIcon) {
    when (icon) {
        AuthFieldIcon.Email -> MailGlyph(tint = TonezenMuted, size = 19.dp)
        AuthFieldIcon.Password -> LockGlyph(tint = TonezenMuted, size = 19.dp)
    }
}
