package com.tonezen.app.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
import com.tonezen.app.ui.components.EyeGlyph
import com.tonezen.app.ui.components.EyeOffGlyph
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal
import dev.chrisbanes.haze.HazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AccountSettingsScreen(
    padding: PaddingValues,
    hazeState: HazeState,
    bottomScrollPadding: Dp,
    displayName: String,
    email: String,
    avatarUrl: String?,
    avatarUploading: Boolean,
    profileSaving: Boolean,
    passwordSaving: Boolean,
    profileError: String?,
    passwordError: String?,
    referralCode: String?,
    referralCodeError: String?,
    passwordFormNonce: Int,
    onBack: () -> Unit,
    onSaveProfile: (displayName: String) -> Unit,
    onChangePassword: (newPassword: String, confirmPassword: String) -> Unit,
    onAvatarPicked: (Uri) -> Unit,
) {
    var name by remember(displayName) { mutableStateOf(displayName) }
    var newPassword by remember(passwordFormNonce) { mutableStateOf("") }
    var confirmPassword by remember(passwordFormNonce) { mutableStateOf("") }
    var passwordVisible by remember(passwordFormNonce) { mutableStateOf(false) }
    var confirmVisible by remember(passwordFormNonce) { mutableStateOf(false) }
    var referralCopied by remember(referralCode) { mutableStateOf(false) }
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val pickAvatarLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val cachedUri = withContext(Dispatchers.IO) {
                cachePickedAvatarUri(context, uri)
            }
            onAvatarPicked(cachedUri ?: uri)
        }
    }

    TonezenFixedHeaderScreen(
        hazeState = hazeState,
        padding = padding,
        onBack = onBack,
        bottomScrollPadding = bottomScrollPadding,
        title = {
            Text(
                "Аккаунт",
                color = TonezenInk,
                fontWeight = FontWeight.SemiBold,
            )
        },
    ) {
        item {
            AccountFormSection(title = "Профиль") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(
                        modifier = Modifier.clickable(enabled = !avatarUploading) {
                            pickAvatarLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                        contentAlignment = Alignment.BottomEnd,
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            ProfileAvatar(avatarUrl = avatarUrl, size = 96.dp, iconSize = 40.dp)
                            if (avatarUploading) {
                                Box(
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(CircleShape)
                                        .background(Color.Black.copy(alpha = 0.45f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(color = TonezenTeal, strokeWidth = 2.dp)
                                }
                            }
                        }
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(TonezenTeal)
                                .border(2.dp, TonezenSurfaceRaised, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "+",
                                color = TonezenAppBg,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium,
                            )
                        }
                    }
                    Text(
                        "Изменить фото",
                        color = TonezenTeal,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.clickable(enabled = !avatarUploading) {
                            pickAvatarLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                            )
                        },
                    )
                }
                AccountLabeledField(
                    value = name,
                    onValueChange = { name = it },
                    label = "Имя",
                    keyboardType = KeyboardType.Text,
                )
                AccountLabeledField(
                    value = email,
                    onValueChange = {},
                    label = "Email",
                    keyboardType = KeyboardType.Email,
                    enabled = false,
                )
                profileError?.let { message ->
                    Text(message, color = TonezenError, style = MaterialTheme.typography.bodySmall)
                }
                Button(
                    onClick = { onSaveProfile(name.trim()) },
                    enabled = name.isNotBlank() && !profileSaving && !avatarUploading,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = TonezenTeal, contentColor = TonezenAppBg),
                ) {
                    Text("Сохранить")
                }
            }
        }
        item {
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
                    onClick = {
                        referralCode?.let {
                            clipboard.setText(AnnotatedString(it))
                            referralCopied = true
                        }
                    },
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
        item {
            AccountFormSection(title = "Смена пароля") {
                AccountLabeledField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    label = "Новый пароль",
                    keyboardType = KeyboardType.Password,
                    hidden = !passwordVisible,
                    showPasswordToggle = true,
                    onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                )
                AccountLabeledField(
                    value = confirmPassword,
                    onValueChange = { confirmPassword = it },
                    label = "Подтвердите пароль",
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
                    Text("Сменить пароль")
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
    ProfileViewModel.ACCOUNT_OFFLINE_ERROR -> "Нужно подключение к интернету"
    ProfileViewModel.PASSWORD_MISMATCH_ERROR -> "Пароли не совпадают"
    ProfileViewModel.NOT_SIGNED_IN_ERROR -> "Войдите в аккаунт"
    ProfileViewModel.PASSWORD_TOO_SHORT_ERROR -> "Пароль должен быть не короче 6 символов"
    ProfileViewModel.PROFILE_UPDATE_FAILED_ERROR -> "Не удалось сохранить профиль"
    ProfileViewModel.PASSWORD_CHANGE_FAILED_ERROR -> "Не удалось сменить пароль"
    ProfileViewModel.REFERRAL_CODE_FAILED_ERROR -> "Не удалось загрузить реферальный код"
    else -> null
}

@Composable
internal fun resolveAvatarUploadError(error: String?): String? = when (error) {
    ProfileViewModel.ACCOUNT_OFFLINE_ERROR -> "Нужно подключение к интернету"
    ProfileViewModel.NOT_SIGNED_IN_ERROR -> "Войдите в аккаунт"
    ProfileViewModel.AVATAR_UPLOAD_FAILED_ERROR -> "Не удалось загрузить аватар"
    else -> null
}
