package com.tonezen.app.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.tonezen.app.R
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenScreenBrush
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenSurfaceMuted
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
fun AuthScreen(
    padding: PaddingValues,
    onLogin: (String, String) -> Unit,
    error: String?,
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
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
                    error = error,
                    canSubmit = canSubmit,
                    onEmailChange = { email = it },
                    onPasswordChange = { password = it },
                    onSubmit = { onLogin(email.trim(), password) },
                )
            }
            item {
                AuthFooterNote()
            }
        }
    }
}

@Composable
private fun AuthSignInForm(
    email: String,
    password: String,
    error: String?,
    canSubmit: Boolean,
    onEmailChange: (String) -> Unit,
    onPasswordChange: (String) -> Unit,
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
        )
        TonezenAuthField(
            value = password,
            onValueChange = onPasswordChange,
            label = stringResource(R.string.password),
            keyboardType = KeyboardType.Password,
            icon = AuthFieldIcon.Password,
            hidden = true,
        )
        Button(
            onClick = onSubmit,
            enabled = canSubmit,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(17.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TonezenTeal,
                contentColor = TonezenAppBg,
                disabledContainerColor = TonezenSurfaceMuted,
                disabledContentColor = TonezenMuted,
            ),
        ) {
            Text(stringResource(R.string.sign_in), fontWeight = FontWeight.SemiBold)
        }
        error?.let {
            Text(
                it,
                color = TonezenError,
                style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
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
    hidden: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(label) },
        leadingIcon = { AuthFieldGlyph(icon) },
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
private fun AuthFieldGlyph(icon: AuthFieldIcon) {
    Canvas(modifier = Modifier.size(19.dp)) {
        when (icon) {
            AuthFieldIcon.Email -> {
                drawRoundRect(
                    color = TonezenMuted,
                    topLeft = Offset(size.width * 0.08f, size.height * 0.20f),
                    size = Size(size.width * 0.84f, size.height * 0.62f),
                    cornerRadius = CornerRadius(size.width * 0.08f, size.width * 0.08f),
                    style = Stroke(width = 2.0f),
                )
                drawLine(
                    color = TonezenMuted,
                    start = Offset(size.width * 0.12f, size.height * 0.28f),
                    end = Offset(size.width * 0.50f, size.height * 0.55f),
                    strokeWidth = 2.0f,
                )
                drawLine(
                    color = TonezenMuted,
                    start = Offset(size.width * 0.88f, size.height * 0.28f),
                    end = Offset(size.width * 0.50f, size.height * 0.55f),
                    strokeWidth = 2.0f,
                )
            }

            AuthFieldIcon.Password -> {
                drawRoundRect(
                    color = TonezenMuted,
                    topLeft = Offset(size.width * 0.20f, size.height * 0.43f),
                    size = Size(size.width * 0.60f, size.height * 0.38f),
                    cornerRadius = CornerRadius(size.width * 0.08f, size.width * 0.08f),
                    style = Stroke(width = 2.0f),
                )
                drawLine(
                    color = TonezenMuted,
                    start = Offset(size.width * 0.34f, size.height * 0.43f),
                    end = Offset(size.width * 0.34f, size.height * 0.31f),
                    strokeWidth = 2.0f,
                )
                drawLine(
                    color = TonezenMuted,
                    start = Offset(size.width * 0.34f, size.height * 0.31f),
                    end = Offset(size.width * 0.66f, size.height * 0.31f),
                    strokeWidth = 2.0f,
                )
                drawLine(
                    color = TonezenMuted,
                    start = Offset(size.width * 0.66f, size.height * 0.31f),
                    end = Offset(size.width * 0.66f, size.height * 0.43f),
                    strokeWidth = 2.0f,
                )
                drawCircle(
                    color = TonezenMuted,
                    radius = size.minDimension * 0.055f,
                    center = Offset(size.width * 0.50f, size.height * 0.61f),
                )
            }
        }
    }
}
