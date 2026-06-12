package com.tonezen.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
import com.tonezen.app.ui.components.EyeGlyph
import com.tonezen.app.ui.components.EyeOffGlyph
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenScreenBrush
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal
import dev.chrisbanes.haze.HazeState

@Composable
internal fun AccountSettingsScreen(
    padding: PaddingValues,
    hazeState: HazeState,
    bottomScrollPadding: Dp,
    displayName: String,
    email: String,
    profileSaving: Boolean,
    passwordSaving: Boolean,
    profileError: String?,
    passwordError: String?,
    passwordFormNonce: Int,
    onBack: () -> Unit,
    onSaveProfile: (displayName: String) -> Unit,
    onChangePassword: (newPassword: String, confirmPassword: String) -> Unit,
) {
    var name by remember(displayName) { mutableStateOf(displayName) }
    var newPassword by remember(passwordFormNonce) { mutableStateOf("") }
    var confirmPassword by remember(passwordFormNonce) { mutableStateOf("") }
    var passwordVisible by remember(passwordFormNonce) { mutableStateOf(false) }
    var confirmVisible by remember(passwordFormNonce) { mutableStateOf(false) }

    TonezenFixedHeaderScreen(
        hazeState = hazeState,
        padding = padding,
        onBack = onBack,
        bottomScrollPadding = bottomScrollPadding,
        title = {
            Text(
                stringResource(R.string.settings_account_page_title),
                color = TonezenInk,
                fontWeight = FontWeight.SemiBold,
            )
        },
    ) {
        item {
            AccountFormSection(title = stringResource(R.string.settings_account_profile_section)) {
                AccountLabeledField(
                    value = name,
                    onValueChange = { name = it },
                    label = stringResource(R.string.settings_account_display_name),
                    keyboardType = KeyboardType.Text,
                )
                AccountLabeledField(
                    value = email,
                    onValueChange = {},
                    label = stringResource(R.string.email),
                    keyboardType = KeyboardType.Email,
                    enabled = false,
                )
                profileError?.let { message ->
                    Text(message, color = TonezenError, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { onSaveProfile(name.trim()) },
                    enabled = name.isNotBlank() && !profileSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TonezenTeal, contentColor = TonezenAppBg),
                ) {
                    Text(stringResource(R.string.settings_account_save))
                }
            }
        }
        item {
            AccountFormSection(title = stringResource(R.string.settings_account_password_section)) {
                AccountLabeledField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = stringResource(R.string.settings_account_new_password),
                    keyboardType = KeyboardType.Password,
                    hidden = !passwordVisible,
                    showPasswordToggle = true,
                    onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                )
                AccountLabeledField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = stringResource(R.string.settings_account_confirm_password),
                    keyboardType = KeyboardType.Password,
                    hidden = !confirmVisible,
                    showPasswordToggle = true,
                    onTogglePasswordVisible = { confirmVisible = !confirmVisible },
                )
                passwordError?.let { message ->
                    Text(message, color = TonezenError, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { onChangePassword(newPassword, confirmPassword) },
                    enabled = newPassword.isNotBlank() && confirmPassword.isNotBlank() && !passwordSaving,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TonezenTeal, contentColor = TonezenAppBg),
                ) {
                    Text(stringResource(R.string.settings_account_change_password))
                }
            }
        }
    }
}

@Composable
private fun AccountFormSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(TonezenSurfaceRaised)
            .border(1.dp, TonezenBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(title, color = TonezenInk, fontWeight = FontWeight.SemiBold)
        content()
    }
}

@Composable
private fun AccountLabeledField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType,
    enabled: Boolean = true,
    hidden: Boolean = false,
    showPasswordToggle: Boolean = false,
    onTogglePasswordVisible: (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        enabled = enabled,
        label = { Text(label) },
        trailingIcon = if (showPasswordToggle && enabled) {
            {
                IconButton(onClick = { onTogglePasswordVisible?.invoke() }) {
                    if (hidden) {
                        EyeGlyph(tint = TonezenMuted, size = 19.dp)
                    } else {
                        EyeOffGlyph(tint = TonezenMuted, size = 19.dp)
                    }
                }
            }
        } else {
            null
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        visualTransformation = if (hidden) PasswordVisualTransformation() else VisualTransformation.None,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = TonezenInk,
            unfocusedTextColor = TonezenInk,
            disabledTextColor = TonezenMuted,
            disabledLabelColor = TonezenMuted,
            focusedContainerColor = TonezenSurface.copy(alpha = 0.72f),
            unfocusedContainerColor = TonezenSurface.copy(alpha = 0.56f),
            disabledContainerColor = TonezenSurface.copy(alpha = 0.4f),
            focusedBorderColor = TonezenTeal,
            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
            disabledBorderColor = Color.White.copy(alpha = 0.12f),
            cursorColor = TonezenTeal,
        ),
    )
}

@Composable
internal fun resolveAccountError(error: String?): String? = when (error) {
    ProfileViewModel.ACCOUNT_OFFLINE_ERROR -> stringResource(R.string.settings_account_offline)
    ProfileViewModel.PASSWORD_MISMATCH_ERROR -> stringResource(R.string.settings_account_password_mismatch)
    ProfileViewModel.NOT_SIGNED_IN_ERROR -> stringResource(R.string.settings_account_not_signed_in)
    ProfileViewModel.PASSWORD_TOO_SHORT_ERROR -> stringResource(R.string.settings_account_password_too_short)
    else -> error
}
