package com.tonezen.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.trackCoverBrush

@Composable
internal fun TrackCoverArt(
    seed: String,
    title: String,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    cornerRadius: Int = 20,
) {
    val brush = remember(seed) { trackCoverBrush(seed) }
    val initials = remember(title) {
        title.trim()
            .split(Regex("\\s+"))
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar()?.toString() }
            .joinToString("")
            .ifBlank { "♪" }
    }
    val shape = RoundedCornerShape(cornerRadius.dp)
    val borderColor = if (isPlaying) TonezenTeal.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f)
    Box(
        modifier = modifier
            .clip(shape)
            .background(brush)
            .border(BorderStroke(if (isPlaying) 2.dp else 1.dp, borderColor), shape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initials,
            color = Color.White.copy(alpha = if (isPlaying) 0.45f else 0.88f),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
