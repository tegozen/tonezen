package com.tonezen.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.components.EyeGlyph
import com.tonezen.app.ui.components.EyeOffGlyph
import com.tonezen.app.ui.components.LockGlyph
import com.tonezen.app.ui.components.MailGlyph
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.TonezenTealStrong

@Composable
internal fun AuthPrimaryButton(
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
internal fun AuthModeActions(
    primaryText: String,
    primaryTag: String,
    onPrimary: () -> Unit,
    secondaryText: String,
    secondaryTag: String,
    onSecondary: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        AuthTextButton(text = primaryText, onClick = onPrimary, modifier = Modifier.testTag(primaryTag))
        AuthTextButton(text = secondaryText, onClick = onSecondary, modifier = Modifier.testTag(secondaryTag))
    }
}

@Composable
internal fun AuthTextButton(
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

internal enum class AuthFieldIcon {
    Email,
    Password,
}

@Composable
internal fun TonezenAuthField(
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
    AuthViewModel.AUTH_LOGIN_FAILED_ERROR -> "Не удалось войти. Проверьте email и пароль."
    AuthViewModel.AUTH_INVITE_CODE_INVALID_ERROR -> "Инвайт-код не подошёл"
    AuthViewModel.AUTH_SIGNUP_FAILED_ERROR -> "Не удалось зарегистрироваться"
    AuthViewModel.AUTH_PASSWORD_MISMATCH_ERROR -> "Пароли не совпадают"
    AuthViewModel.AUTH_PASSWORD_TOO_SHORT_ERROR -> "Пароль должен быть не короче 12 символов"
    AuthViewModel.AUTH_RECOVERY_FAILED_ERROR -> "Не удалось отправить ссылку"
    else -> "Не удалось войти. Проверьте email и пароль."
}

@Composable
private fun AuthFieldGlyph(icon: AuthFieldIcon) {
    when (icon) {
        AuthFieldIcon.Email -> MailGlyph(tint = TonezenMuted, size = 19.dp)
        AuthFieldIcon.Password -> LockGlyph(tint = TonezenMuted, size = 19.dp)
    }
}
