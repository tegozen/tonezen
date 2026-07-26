package com.tonezen.app.ui.auth

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.tonezen.app.ui.testing.TestTags
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun AuthLoginFields(
    email: String,
    password: String,
    passwordVisible: Boolean,
    canSubmit: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onSubmit: () -> Unit,
    onModeChange: (AuthFormMode) -> Unit,
) {
    TonezenAuthField(
        value = email,
        onValueChange = onEmailChange,
        label = "Email",
        keyboardType = KeyboardType.Email,
        icon = AuthFieldIcon.Email,
        modifier = Modifier.testTag(TestTags.AUTH_EMAIL),
    )
    TonezenAuthField(
        value = password,
        onValueChange = onPasswordChange,
        label = "Пароль",
        keyboardType = KeyboardType.Password,
        icon = AuthFieldIcon.Password,
        hidden = !passwordVisible,
        showPasswordToggle = true,
        passwordVisible = passwordVisible,
        onTogglePasswordVisible = onTogglePasswordVisible,
        modifier = Modifier.testTag(TestTags.AUTH_PASSWORD),
    )
    AuthPrimaryButton(
        text = "Войти",
        enabled = canSubmit,
        onClick = onSubmit,
        modifier = Modifier.testTag(TestTags.AUTH_SIGN_IN),
    )
    AuthModeActions(
        primaryText = "Забыли пароль?",
        primaryTag = TestTags.AUTH_SHOW_RECOVERY,
        onPrimary = { onModeChange(AuthFormMode.Recovery) },
        secondaryText = "Нет аккаунта",
        secondaryTag = TestTags.AUTH_SHOW_SIGN_UP,
        onSecondary = { onModeChange(AuthFormMode.Signup) },
    )
}

@Composable
internal fun AuthSignupFields(
    inviteCode: String,
    inviteCodeVerified: Boolean,
    signupEmail: String,
    signupName: String,
    signupPassword: String,
    signupConfirmPassword: String,
    signupPasswordVisible: Boolean,
    canVerifyInvite: Boolean,
    canSignup: Boolean,
    onInviteCodeChange: (String) -> Unit,
    onVerifyInviteCode: () -> Unit,
    onSignupEmailChange: (String) -> Unit,
    onSignupNameChange: (String) -> Unit,
    onSignupPasswordChange: (String) -> Unit,
    onSignupConfirmPasswordChange: (String) -> Unit,
    onToggleSignupPasswordVisible: () -> Unit,
    onSignup: () -> Unit,
    onModeChange: (AuthFormMode) -> Unit,
) {
    TonezenAuthField(
        value = inviteCode,
        onValueChange = onInviteCodeChange,
        label = "Инвайт-код",
        keyboardType = KeyboardType.Text,
        icon = AuthFieldIcon.Password,
        modifier = Modifier.testTag(TestTags.AUTH_INVITE_CODE),
    )
    if (!inviteCodeVerified) {
        AuthPrimaryButton(
            text = "Проверить код",
            enabled = canVerifyInvite,
            onClick = onVerifyInviteCode,
            modifier = Modifier.testTag(TestTags.AUTH_VERIFY_INVITE),
        )
    } else {
        TonezenAuthField(
            value = signupEmail,
            onValueChange = onSignupEmailChange,
            label = "Email",
            keyboardType = KeyboardType.Email,
            icon = AuthFieldIcon.Email,
            modifier = Modifier.testTag(TestTags.AUTH_SIGNUP_EMAIL),
        )
        TonezenAuthField(
            value = signupName,
            onValueChange = onSignupNameChange,
            label = "Имя",
            keyboardType = KeyboardType.Text,
            icon = AuthFieldIcon.Email,
            modifier = Modifier.testTag(TestTags.AUTH_SIGNUP_NAME),
        )
        TonezenAuthField(
            value = signupPassword,
            onValueChange = onSignupPasswordChange,
            label = "Пароль",
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
            label = "Подтвердите пароль",
            keyboardType = KeyboardType.Password,
            icon = AuthFieldIcon.Password,
            hidden = !signupPasswordVisible,
            modifier = Modifier.testTag(TestTags.AUTH_SIGNUP_CONFIRM),
        )
        AuthPrimaryButton(
            text = "Создать аккаунт",
            enabled = canSignup,
            onClick = onSignup,
            modifier = Modifier.testTag(TestTags.AUTH_SIGN_UP),
        )
    }
    AuthTextButton(
        text = "Уже есть аккаунт",
        onClick = { onModeChange(AuthFormMode.Login) },
    )
}

@Composable
internal fun AuthRecoveryFields(
    recoveryEmail: String,
    passwordRecoverySent: Boolean,
    canRecover: Boolean,
    onRecoveryEmailChange: (String) -> Unit,
    onPasswordRecovery: () -> Unit,
    onModeChange: (AuthFormMode) -> Unit,
) {
    Text(
        "Восстановление пароля",
        color = TonezenInk,
        fontWeight = FontWeight.SemiBold,
    )
    Text(
        "Введите email аккаунта. Если он зарегистрирован, мы отправим ссылку для сброса пароля.",
        color = TonezenMuted,
        style = MaterialTheme.typography.bodySmall,
    )
    TonezenAuthField(
        value = recoveryEmail,
        onValueChange = onRecoveryEmailChange,
        label = "Email",
        keyboardType = KeyboardType.Email,
        icon = AuthFieldIcon.Email,
        modifier = Modifier.testTag(TestTags.AUTH_RECOVERY_EMAIL),
    )
    AuthPrimaryButton(
        text = "Отправить ссылку",
        enabled = canRecover,
        onClick = onPasswordRecovery,
        modifier = Modifier.testTag(TestTags.AUTH_RECOVERY_SUBMIT),
    )
    if (passwordRecoverySent) {
        Text(
            "Если аккаунт найден, письмо уже отправлено.",
            color = TonezenTeal,
            style = MaterialTheme.typography.bodySmall,
        )
    }
    AuthTextButton(
        text = "Назад ко входу",
        onClick = { onModeChange(AuthFormMode.Login) },
    )
}
