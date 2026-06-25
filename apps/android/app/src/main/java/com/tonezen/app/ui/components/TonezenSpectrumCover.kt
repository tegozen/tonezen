package com.tonezen.app.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.trackCoverBrush
import kotlinx.coroutines.isActive
import kotlin.math.PI

@Composable
internal fun SpectrumCoverArt(
    seed: String,
    modifier: Modifier = Modifier,
    isPlaying: Boolean = false,
    cornerRadius: Int = 24,
    downloadProgress: Float? = null,
) {
    val brush = remember(seed) { trackCoverBrush(seed) }
    val bars = remember(seed) { buildSpectrumBars(seed) }
    val phase = remember { Animatable(0f) }
    val shape = RoundedCornerShape(cornerRadius.dp)
    val borderColor = if (isPlaying) TonezenTeal.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f)

    LaunchedEffect(isPlaying, seed) {
        if (!isPlaying) {
            phase.snapTo(0f)
            return@LaunchedEffect
        }
        while (isActive) {
            phase.animateTo(
                targetValue = (PI * 2).toFloat(),
                animationSpec = tween(durationMillis = 1150, easing = LinearEasing),
            )
            phase.snapTo(0f)
        }
    }

    Box(
        modifier = modifier
            .size(168.dp)
            .clip(shape)
            .background(brush)
            .border(BorderStroke(if (isPlaying) 2.dp else 1.dp, borderColor), shape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawCircle(
                color = TonezenTeal.copy(alpha = if (isPlaying) 0.18f else 0.1f),
                radius = size.minDimension * 0.38f,
                center = Offset(size.width * 0.5f, size.height * 0.5f),
            )
            drawCircle(
                color = Color.White.copy(alpha = 0.06f),
                radius = size.minDimension * 0.28f,
                center = Offset(size.width * 0.24f, size.height * 0.22f),
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(18.dp),
        ) {
            val gap = 3.dp.toPx()
            val barWidth = ((size.width - gap * (bars.size - 1)) / bars.size).coerceAtLeast(2.dp.toPx())
            val maxHeight = size.height
            bars.forEachIndexed { index, bar ->
                val height = maxHeight * spectrumBarHeightFraction(bar, index, phase.value, isPlaying)
                val left = index * (barWidth + gap)
                val top = (size.height - height) / 2f
                val color = if (index % 4 == 0) {
                    Color.White.copy(alpha = if (isPlaying) 0.62f else 0.42f)
                } else {
                    TonezenTeal.copy(alpha = if (isPlaying) 0.88f else 0.58f)
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }
        }
        downloadProgress?.let { progress ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.58f)),
                contentAlignment = Alignment.Center,
            ) {
                CoverDownloadProgress(progress = progress)
            }
        }
    }
}
