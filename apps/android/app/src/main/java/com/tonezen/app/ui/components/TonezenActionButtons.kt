package com.tonezen.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal

private val TonezenSheetActionButtonShape = RoundedCornerShape(16.dp)
private val TonezenSheetActionButtonHeight = 52.dp

@Composable
internal fun TonezenSheetSecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(TonezenSheetActionButtonHeight),
        shape = TonezenSheetActionButtonShape,
        border = BorderStroke(1.dp, TonezenBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = TonezenSurfaceRaised,
            contentColor = TonezenInk,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun TonezenSheetPrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(TonezenSheetActionButtonHeight),
        shape = TonezenSheetActionButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = TonezenTeal,
            contentColor = TonezenAppBg,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp),
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
internal fun ActionButton(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        border = BorderStroke(1.dp, TonezenBorder),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TonezenInk),
    ) {
        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelSmall)
    }
}
