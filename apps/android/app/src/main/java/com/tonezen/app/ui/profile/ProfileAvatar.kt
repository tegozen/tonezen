package com.tonezen.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.tonezen.app.ui.components.ProfileGlyph
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun ProfileAvatar(
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 58.dp,
    borderAlpha: Float = 0.16f,
    iconSize: Dp = 28.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .border(2.dp, Color.White.copy(alpha = borderAlpha), CircleShape)
            .padding(2.dp)
            .clip(CircleShape)
            .background(TonezenSurfaceRaised),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUrl.isNullOrBlank()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.matchParentSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            ProfileGlyph(tint = TonezenTeal, size = iconSize)
        }
    }
}
