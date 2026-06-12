package com.tonezen.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted

@Composable
internal fun SearchGlyph() {
    Canvas(modifier = Modifier.size(18.dp)) {
        drawCircle(color = TonezenMuted, radius = size.minDimension * 0.34f, style = Stroke(width = 2.4f))
        drawLine(
            color = TonezenMuted,
            start = Offset(size.width * 0.68f, size.height * 0.68f),
            end = Offset(size.width * 0.95f, size.height * 0.95f),
            strokeWidth = 2.4f,
        )
    }
}

@Composable
internal fun OverflowGlyph() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val dotRadius = size.minDimension * 0.09f
        listOf(0.25f, 0.50f, 0.75f).forEach { y ->
            drawCircle(
                color = TonezenMuted,
                radius = dotRadius,
                center = Offset(size.width * 0.5f, size.height * y),
            )
        }
    }
}

@Composable
internal fun QueueGlyph() {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        repeat(3) {
            Box(
                modifier = Modifier
                    .width(26.dp)
                    .height(2.dp)
                    .background(TonezenInk.copy(alpha = 0.9f), RoundedCornerShape(2.dp)),
            )
        }
    }
}
