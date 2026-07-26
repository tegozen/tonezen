package com.tonezen.app.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.components.ProgressBar
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun WaveformProgressBar(
    progress: Float,
    peaks: List<Int>,
    onSeek: ((Float) -> Unit)? = null,
) {
    if (peaks.size != 64 || peaks.any { it !in 0..100 }) {
        ProgressBar(progress = progress, onSeek = onSeek)
        return
    }
    val seek = onSeek
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (seek != null) 40.dp else 24.dp)
            .then(
                if (seek != null) {
                    Modifier.pointerInput(seek) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            fun seekTo(x: Float) {
                                if (size.width > 0) {
                                    seek((x / size.width).coerceIn(0f, 1f))
                                }
                            }
                            seekTo(down.position.x)
                            drag(down.id) { change ->
                                seekTo(change.position.x)
                                change.consume()
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        val desiredGap = 2.dp.toPx()
        val minBarWidth = 2.dp.toPx()
        val gap = if (peaks.size > 1) {
            ((size.width - minBarWidth * peaks.size) / (peaks.size - 1))
                .coerceIn(1.dp.toPx(), desiredGap)
        } else {
            0f
        }
        val barWidth = ((size.width - gap * (peaks.size - 1)) / peaks.size)
            .coerceAtLeast(1.dp.toPx())
        val maxHeight = 32.dp.toPx().coerceAtMost(size.height)
        val minHeight = 4.dp.toPx()
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)

        fun drawBars(color: Color) {
            peaks.forEachIndexed { index, peak ->
                val fraction = (peak / 100f).coerceIn(0f, 1f)
                val height = minHeight + (maxHeight - minHeight) * fraction
                val left = index * (barWidth + gap)
                val top = (size.height - height) / 2f
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, height),
                    cornerRadius = radius,
                )
            }
        }

        drawBars(Color.White.copy(alpha = 0.16f))
        clipRect(right = size.width * progress.coerceIn(0f, 1f)) {
            drawBars(TonezenTeal)
        }
    }
}
