package com.tonezen.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted

@Composable
internal fun CheckCircleGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.CheckCircle, modifier, tint, size)
}

@Composable
internal fun SkipPreviousGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 22.dp) {
    Canvas(modifier.then(Modifier.size(size))) {
        val barWidth = this.size.width * 0.1f
        val barHeight = this.size.height * 0.56f
        val barTop = (this.size.height - barHeight) / 2f
        drawRoundRect(
            color = tint,
            topLeft = Offset(this.size.width * 0.14f, barTop),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
        )
        val triangle = Path().apply {
            moveTo(this@Canvas.size.width * 0.76f, this@Canvas.size.height * 0.2f)
            lineTo(this@Canvas.size.width * 0.34f, this@Canvas.size.height * 0.5f)
            lineTo(this@Canvas.size.width * 0.76f, this@Canvas.size.height * 0.8f)
            close()
        }
        drawPath(triangle, tint)
    }
}

@Composable
internal fun SkipNextGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 22.dp) {
    Canvas(modifier.then(Modifier.size(size))) {
        val barWidth = this.size.width * 0.1f
        val barHeight = this.size.height * 0.56f
        val barTop = (this.size.height - barHeight) / 2f
        drawRoundRect(
            color = tint,
            topLeft = Offset(this.size.width * 0.76f, barTop),
            size = Size(barWidth, barHeight),
            cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
        )
        val triangle = Path().apply {
            moveTo(this@Canvas.size.width * 0.24f, this@Canvas.size.height * 0.2f)
            lineTo(this@Canvas.size.width * 0.66f, this@Canvas.size.height * 0.5f)
            lineTo(this@Canvas.size.width * 0.24f, this@Canvas.size.height * 0.8f)
            close()
        }
        drawPath(triangle, tint)
    }
}

@Composable
internal fun StorageGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Storage, modifier, tint, size)
}

@Composable
internal fun SyncGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Sync, modifier, tint, size)
}

@Composable
internal fun LockGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Lock, modifier, tint, size)
}

@Composable
internal fun WarningGlyph(modifier: Modifier = Modifier, tint: Color = TonezenAmber, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Warning, modifier, tint, size)
}

@Composable
internal fun PlayGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Play, modifier, tint, size)
}

@Composable
internal fun PauseGlyph(modifier: Modifier = Modifier, tint: Color = TonezenInk, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Pause, modifier, tint, size)
}

@Composable
internal fun EyeGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.Eye, modifier, tint, size)
}

@Composable
internal fun EyeOffGlyph(modifier: Modifier = Modifier, tint: Color = TonezenMuted, size: Dp = 20.dp) {
    TonezenSvgGlyph(TonezenSvgAsset.EyeOff, modifier, tint, size)
}
