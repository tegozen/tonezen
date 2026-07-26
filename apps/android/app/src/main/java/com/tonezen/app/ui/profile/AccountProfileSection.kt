package com.tonezen.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun AccountProfileSection(
    name: String,
    onNameChange: (String) -> Unit,
    email: String,
    avatarUrl: String?,
    avatarUploading: Boolean,
    profileSaving: Boolean,
    profileError: String?,
    onPickAvatar: () -> Unit,
    onSaveProfile: () -> Unit,
) {
    AccountFormSection(title = "Профиль") {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier.clickable(enabled = !avatarUploading, onClick = onPickAvatar),
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
                modifier = Modifier.clickable(enabled = !avatarUploading, onClick = onPickAvatar),
            )
        }
        AccountLabeledField(
            value = name,
            onValueChange = onNameChange,
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
            onClick = onSaveProfile,
            enabled = name.isNotBlank() && !profileSaving && !avatarUploading,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TonezenTeal, contentColor = TonezenAppBg),
        ) {
            Text("Сохранить")
        }
    }
}
