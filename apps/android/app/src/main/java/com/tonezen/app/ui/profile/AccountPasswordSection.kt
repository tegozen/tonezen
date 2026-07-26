package com.tonezen.app.ui.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun AccountReferralSection(
    referralCode: String?,
    referralCodeError: String?,
    referralCopied: Boolean,
    onCopyReferralCode: () -> Unit,
) {
    AccountFormSection(title = "Реферальный код") {
        Text(
            "Дайте этот код человеку, которому хотите открыть регистрацию.",
            color = TonezenMuted,
            style = MaterialTheme.typography.bodySmall,
        )
        AccountLabeledField(
            value = referralCode.orEmpty(),
            onValueChange = {},
            label = "Инвайт-код",
            keyboardType = KeyboardType.Text,
            enabled = false,
        )
        referralCodeError?.let { message ->
            Text(message, color = TonezenError, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = onCopyReferralCode,
            enabled = !referralCode.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TonezenTeal, contentColor = TonezenAppBg),
        ) {
            Text(
                if (referralCopied) {
                    "Скопировано"
                } else {
                    "Скопировать"
                },
            )
        }
    }
}

@Composable
internal fun AccountPasswordSection(
    currentPassword: String,
    onCurrentPasswordChange: (String) -> Unit,
    currentVisible: Boolean,
    onToggleCurrentVisible: () -> Unit,
    newPassword: String,
    onNewPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisible: () -> Unit,
    confirmPassword: String,
    onConfirmPasswordChange: (String) -> Unit,
    confirmVisible: Boolean,
    onToggleConfirmVisible: () -> Unit,
    passwordError: String?,
    passwordSaving: Boolean,
    onChangePassword: () -> Unit,
) {
    AccountFormSection(title = "Смена пароля") {
        AccountLabeledField(
            value = currentPassword,
            onValueChange = onCurrentPasswordChange,
            label = "Текущий пароль",
            keyboardType = KeyboardType.Password,
            hidden = !currentVisible,
            showPasswordToggle = true,
            onTogglePasswordVisible = onToggleCurrentVisible,
        )
        AccountLabeledField(
            value = newPassword,
            onValueChange = onNewPasswordChange,
            label = "Новый пароль",
            keyboardType = KeyboardType.Password,
            hidden = !passwordVisible,
            showPasswordToggle = true,
            onTogglePasswordVisible = onTogglePasswordVisible,
        )
        AccountLabeledField(
            value = confirmPassword,
            onValueChange = onConfirmPasswordChange,
            label = "Подтвердите пароль",
            keyboardType = KeyboardType.Password,
            hidden = !confirmVisible,
            showPasswordToggle = true,
            onTogglePasswordVisible = onToggleConfirmVisible,
        )
        passwordError?.let { message ->
            Text(message, color = TonezenError, style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = onChangePassword,
            enabled = currentPassword.isNotBlank() &&
                newPassword.isNotBlank() &&
                confirmPassword.isNotBlank() &&
                !passwordSaving,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TonezenTeal, contentColor = TonezenAppBg),
        ) {
            Text("Сменить пароль")
        }
    }
}
