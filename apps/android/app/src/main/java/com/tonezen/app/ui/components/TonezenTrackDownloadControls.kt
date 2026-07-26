package com.tonezen.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.ui.testing.TestTags
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun TrackDownloadButton(
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isDownloading = progress != null
    val downloadLabel = "Скачать"
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .testTag(TestTags.TRACK_DOWNLOAD)
            .semantics { contentDescription = downloadLabel }
            .then(
                if (isDownloading) {
                    Modifier
                } else {
                    Modifier.clickable(enabled = enabled, onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (isDownloading) {
            val sweep = 360f * progress.coerceIn(0f, 1f)
            val showIndeterminate = progress <= 0f
            Canvas(modifier = Modifier.size(36.dp)) {
                val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                drawArc(
                    color = Color.White.copy(alpha = 0.16f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    style = stroke,
                )
                drawArc(
                    color = TonezenTeal,
                    startAngle = -90f,
                    sweepAngle = if (showIndeterminate) 90f else sweep,
                    useCenter = false,
                    style = stroke,
                )
            }
            Text(
                text = if (showIndeterminate) "…" else "${(progress * 100).toInt()}%",
                color = TonezenTeal,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
            )
        } else {
            DownloadGlyph(tint = TonezenMuted, size = 18.dp)
        }
    }
}

@Composable
internal fun CompactMediaPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    downloadProgress: Float? = null,
) {
    val isDownloading = downloadProgress != null
    val background = if (isPlaying) {
        Brush.linearGradient(listOf(Color(0xFF14B8A6), Color(0xFF0D9488), Color(0xFF0F766E)))
    } else {
        Brush.linearGradient(listOf(Color(0xFF5EEAD4), Color(0xFF14B8A6), Color(0xFF0D9488)))
    }
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .background(background)
            .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.22f)), CircleShape)
            .then(
                if (isDownloading) {
                    Modifier
                } else {
                    Modifier.clickable(onClick = onClick)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            isDownloading -> {
                val sweep = 360f * downloadProgress.coerceIn(0f, 1f)
                val showIndeterminate = downloadProgress <= 0f
                Canvas(modifier = Modifier.size(36.dp)) {
                    val stroke = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    drawArc(
                        color = Color.White.copy(alpha = 0.16f),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = stroke,
                    )
                    drawArc(
                        color = TonezenAppBg,
                        startAngle = -90f,
                        sweepAngle = if (showIndeterminate) 90f else sweep,
                        useCenter = false,
                        style = stroke,
                    )
                }
                Text(
                    text = if (showIndeterminate) "…" else "${(downloadProgress * 100).toInt()}%",
                    color = TonezenAppBg,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                )
            }
            isPlaying -> PauseGlyph(tint = TonezenAppBg, size = 18.dp)
            else -> PlayGlyph(tint = TonezenAppBg, size = 18.dp)
        }
    }
}

@Composable
internal fun TrackDownloadedIndicator(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(36.dp)
            .testTag(TestTags.TRACK_DOWNLOADED),
        contentAlignment = Alignment.Center,
    ) {
        CheckCircleGlyph(tint = TonezenTeal, size = 18.dp)
    }
}
