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
    error: String?,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val canSubmit = email.isNotBlank() && password.isNotBlank()

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
                    email = email,
                    password = password,
                    passwordVisible = passwordVisible,
                    error = error,
                    canSubmit = canSubmit,
                    onEmailChange = { email = it },
                    onPasswordChange = { password = it },
                    onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                    onSubmit = { onLogin(email.trim(), password) },
                )
            }
        }
    }
}

@Composable
private fun AuthSignInForm(
    email: String,
    password: String,
    passwordVisible: Boolean,
    error: String?,
    canSubmit: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
    onTogglePasswordVisible: () -> Unit,
    onSubmit: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
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
        AuthSignInButton(
            enabled = canSubmit,
            onClick = onSubmit,
        )
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
private fun AuthSignInButton(
    enabled: Boolean,
    onClick: () -> Unit,
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
            .testTag(TestTags.AUTH_SIGN_IN)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.sign_in),
            color = TonezenAppBg.copy(alpha = if (enabled) 1f else 0.72f),
            fontWeight = FontWeight.SemiBold,
        )
    }
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
    else -> stringResource(R.string.auth_login_failed)
}

@Composable
private fun AuthFieldGlyph(icon: AuthFieldIcon) {
    when (icon) {
        AuthFieldIcon.Email -> MailGlyph(tint = TonezenMuted, size = 19.dp)
        AuthFieldIcon.Password -> LockGlyph(tint = TonezenMuted, size = 19.dp)
    }
}
