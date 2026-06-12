package com.tonezen.app.ui.auth

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.ui.components.PlayTriangle
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenGreen
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
private fun AuthStarField(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        drawCircle(
            color = TonezenTeal.copy(alpha = 0.05f),
            radius = size.minDimension * 0.58f,
            center = Offset(size.width * 0.86f, size.height * 0.08f),
        )
        drawCircle(
            color = TonezenAmber.copy(alpha = 0.06f),
            radius = size.minDimension * 0.42f,
            center = Offset(size.width * 0.10f, size.height * 0.35f),
        )
        listOf(
            Offset(0.18f, 0.10f) to 0.004f,
            Offset(0.84f, 0.17f) to 0.003f,
            Offset(0.68f, 0.31f) to 0.0027f,
            Offset(0.24f, 0.48f) to 0.0028f,
            Offset(0.78f, 0.59f) to 0.0032f,
            Offset(0.46f, 0.72f) to 0.0025f,
        ).forEach { (point, radius) ->
            drawCircle(
                color = Color.White.copy(alpha = 0.16f),
                radius = size.minDimension * radius,
                center = Offset(size.width * point.x, size.height * point.y),
            )
        }
    }
}

@Composable
private fun AuthIntroPanel() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            stringResource(R.string.app_name),
            color = TonezenInk,
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
        )
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                stringResource(R.string.auth_headline),
                color = TonezenInk,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                stringResource(R.string.auth_body),
                color = TonezenMuted,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AuthPill(stringResource(R.string.auth_offline_badge), TonezenTeal)
            AuthPill(stringResource(R.string.auth_sync_badge), TonezenGreen)
        }
        AuthMediaStack()
    }
}

@Composable
private fun AuthPill(label: String, tone: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(tone.copy(alpha = 0.12f))
            .border(BorderStroke(1.dp, tone.copy(alpha = 0.24f)), RoundedCornerShape(999.dp))
            .padding(horizontal = 9.dp, vertical = 5.dp),
    ) {
        Text(label, color = tone, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

@Composable
private fun AuthMediaStack() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(190.dp)
            .padding(top = 2.dp),
    ) {
        AuthMiniCover(
            title = stringResource(R.string.auth_cover_atomic),
            brush = Brush.verticalGradient(listOf(Color(0xFFF5E8CE), Color(0xFFC9AA78))),
            contentColor = Color(0xFF6D4C2F),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = 18.dp, y = 4.dp)
                .size(width = 118.dp, height = 158.dp)
                .graphicsLayer(rotationZ = -7f),
        )
        AuthMiniCover(
            title = stringResource(R.string.auth_cover_midnight),
            brush = Brush.verticalGradient(listOf(Color(0xFF06111D), Color(0xFF12314C), Color(0xFF06111D))),
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = (-4).dp)
                .size(width = 142.dp, height = 178.dp)
                .graphicsLayer(rotationZ = 1.5f),
        )
        AuthMiniCover(
            title = stringResource(R.string.auth_cover_body),
            brush = Brush.verticalGradient(listOf(Color(0xFF8D2E1B), Color(0xFFD65B28))),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = (-18).dp, y = 6.dp)
                .size(width = 112.dp, height = 150.dp)
                .graphicsLayer(rotationZ = 7f),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .offset(y = (-2).dp)
                .size(48.dp)
                .clip(CircleShape)
                .background(Color(0xFFF5E9D6)),
            contentAlignment = Alignment.Center,
        ) {
            PlayTriangle(tint = TonezenAppBg, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun AuthMiniCover(
    title: String,
    brush: Brush,
    contentColor: Color = TonezenInk,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(brush)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.16f)), RoundedCornerShape(12.dp))
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            title,
            color = contentColor,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 4,
        )
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
                style = MaterialTheme.typography.bodySmall,
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

@Composable
private fun AuthFooterNote() {
    Text(
        stringResource(R.string.offline_playback_note),
        color = TonezenMuted,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp),
    )
}
