package com.tonezen.app.ui.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.components.EyeGlyph
import com.tonezen.app.ui.components.EyeOffGlyph
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun AccountLabeledField(
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
