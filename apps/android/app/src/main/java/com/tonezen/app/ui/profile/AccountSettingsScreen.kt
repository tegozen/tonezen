package com.tonezen.app.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
import com.tonezen.app.ui.theme.TonezenInk
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
    onChangePassword: (currentPassword: String, newPassword: String, confirmPassword: String) -> Unit,
    onAvatarPicked: (Uri) -> Unit,
) {
    var name by remember(displayName) { mutableStateOf(displayName) }
    var currentPassword by remember(passwordFormNonce) { mutableStateOf("") }
    var newPassword by remember(passwordFormNonce) { mutableStateOf("") }
    var confirmPassword by remember(passwordFormNonce) { mutableStateOf("") }
    var currentVisible by remember(passwordFormNonce) { mutableStateOf(false) }
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
    val onPickAvatar = {
        pickAvatarLauncher.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
        )
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
            AccountProfileSection(
                name = name,
                onNameChange = { name = it },
                email = email,
                avatarUrl = avatarUrl,
                avatarUploading = avatarUploading,
                profileSaving = profileSaving,
                profileError = profileError,
                onPickAvatar = onPickAvatar,
                onSaveProfile = { onSaveProfile(name.trim()) },
            )
        }
        item {
            AccountReferralSection(
                referralCode = referralCode,
                referralCodeError = referralCodeError,
                referralCopied = referralCopied,
                onCopyReferralCode = {
                    referralCode?.let {
                        clipboard.setText(AnnotatedString(it))
                        referralCopied = true
                    }
                },
            )
        }
        item {
            AccountPasswordSection(
                currentPassword = currentPassword,
                onCurrentPasswordChange = { currentPassword = it },
                currentVisible = currentVisible,
                onToggleCurrentVisible = { currentVisible = !currentVisible },
                newPassword = newPassword,
                onNewPasswordChange = { newPassword = it },
                passwordVisible = passwordVisible,
                onTogglePasswordVisible = { passwordVisible = !passwordVisible },
                confirmPassword = confirmPassword,
                onConfirmPasswordChange = { confirmPassword = it },
                confirmVisible = confirmVisible,
                onToggleConfirmVisible = { confirmVisible = !confirmVisible },
                passwordError = passwordError,
                passwordSaving = passwordSaving,
                onChangePassword = { onChangePassword(currentPassword, newPassword, confirmPassword) },
            )
        }
    }
}
