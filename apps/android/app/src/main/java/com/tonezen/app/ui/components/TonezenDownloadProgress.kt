package com.tonezen.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.theme.TonezenAppBg

@Composable
internal fun DownloadProgressRing(progress: Float) {
    val sweep = 360f * progress.coerceIn(0f, 1f)
    val showIndeterminate = progress <= 0f
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(56.dp)) {
            drawArc(
                color = Color.White.copy(alpha = 0.2f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 4.dp.toPx()),
            )
            if (showIndeterminate) {
                drawArc(
                    color = TonezenAppBg,
                    startAngle = -90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
            } else {
                drawArc(
                    color = TonezenAppBg,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(
                        width = 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
            }
        }
        Text(
            text = if (showIndeterminate) "…" else "${(progress * 100).toInt()}%",
            color = TonezenAppBg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
        )
    }
}
