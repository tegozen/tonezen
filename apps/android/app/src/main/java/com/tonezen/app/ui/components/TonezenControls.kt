package com.tonezen.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.Canvas
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted

@Composable
internal fun RoundControl(label: String, outlined: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .border(BorderStroke(if (outlined) 1.4.dp else 0.dp, TonezenInk), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = TonezenInk, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun PlayButton(isPlaying: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(Color(0xFFF5E9D6))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlaying) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(width = 6.dp, height = 26.dp).background(TonezenAppBg, RoundedCornerShape(2.dp)))
                Box(Modifier.size(width = 6.dp, height = 26.dp).background(TonezenAppBg, RoundedCornerShape(2.dp)))
            }
        } else {
            PlayTriangle(tint = TonezenAppBg, modifier = Modifier.size(30.dp))
        }
    }
}

@Composable
internal fun PlayTriangle(tint: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.28f, size.height * 0.18f)
            lineTo(size.width * 0.28f, size.height * 0.82f)
            lineTo(size.width * 0.82f, size.height * 0.50f)
            close()
        }
        drawPath(path, tint)
    }
}

@Composable
internal fun ProgressBar(progress: Float, onSeek: ((Float) -> Unit)? = null) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(Color.White.copy(alpha = 0.16f))
            .then(
                if (onSeek != null) {
                    Modifier.clickable { onSeek(progress.coerceIn(0f, 1f)) }
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(4.dp)
                .background(TonezenAmber),
        )
    }
}

@Composable
internal fun PlayingBars(active: Boolean) {
    Row(
        modifier = Modifier.width(18.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        listOf(8.dp, 14.dp, 10.dp).forEach { height ->
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(if (active) height else 3.dp)
                    .background(if (active) TonezenAmber else TonezenMuted, RoundedCornerShape(2.dp)),
            )
        }
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
