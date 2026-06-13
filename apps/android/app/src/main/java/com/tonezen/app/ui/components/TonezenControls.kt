package com.tonezen.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.input.pointer.pointerInput
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenSurfaceRaised
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun RoundControl(
    label: String,
    outlined: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    onClick: () -> Unit,
) {
    val background = Color.White.copy(alpha = if (enabled) 0.08f else 0.04f)
    val borderColor = if (outlined) Color.White.copy(alpha = if (enabled) 0.16f else 0.08f) else Color.Transparent
    val textStyle = if (size < 48.dp) {
        MaterialTheme.typography.labelSmall
    } else {
        MaterialTheme.typography.labelMedium
    }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(BorderStroke(if (outlined) 1.dp else 0.dp, borderColor), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = TonezenInk.copy(alpha = if (enabled) 0.92f else 0.38f),
            style = textStyle,
            fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun RoundIconControl(
    outlined: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 48.dp,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val background = Color.White.copy(alpha = if (enabled) 0.08f else 0.04f)
    val borderColor = if (outlined) Color.White.copy(alpha = if (enabled) 0.16f else 0.08f) else Color.Transparent
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .border(BorderStroke(if (outlined) 1.dp else 0.dp, borderColor), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

@Composable
internal fun PlayButton(
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    downloadProgress: Float? = null,
    onClick: () -> Unit,
) {
    val background = if (isPlaying) {
        androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Color(0xFF14B8A6), Color(0xFF0D9488), Color(0xFF0F766E)),
        )
    } else {
        androidx.compose.ui.graphics.Brush.linearGradient(
            listOf(Color(0xFF5EEAD4), Color(0xFF14B8A6), Color(0xFF0D9488)),
        )
    }
    val downloading = downloadProgress != null
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(background)
            .border(BorderStroke(2.dp, Color.White.copy(alpha = 0.22f)), CircleShape)
            .then(if (downloading) Modifier else Modifier.clickable(onClick = onClick)),
        contentAlignment = Alignment.Center,
    ) {
        if (downloading) {
            DownloadProgressRing(progress = downloadProgress)
        } else if (isPlaying) {
            PauseGlyph(tint = TonezenAppBg, size = 30.dp)
        } else {
            PlayGlyph(tint = TonezenAppBg, size = 30.dp)
        }
    }
}

@Composable
private fun DownloadProgressRing(progress: Float) {
    val sweep = 360f * progress.coerceIn(0f, 1f)
    val showIndeterminate = progress <= 0f
    Box(contentAlignment = Alignment.Center) {
        androidx.compose.foundation.Canvas(modifier = Modifier.size(56.dp)) {
            drawArc(
                color = Color.White.copy(alpha = 0.2f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4.dp.toPx()),
            )
            if (showIndeterminate) {
                drawArc(
                    color = TonezenAppBg,
                    startAngle = -90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 4.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    ),
                )
            } else {
                drawArc(
                    color = TonezenAppBg,
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = androidx.compose.ui.graphics.drawscope.Stroke(
                        width = 4.dp.toPx(),
                        cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    ),
                )
            }
        }
        Text(
            text = if (showIndeterminate) "…" else "${(progress * 100).toInt()}%",
            color = TonezenAppBg,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
        )
    }
}

@Composable
internal fun ProgressBar(progress: Float, onSeek: ((Float) -> Unit)? = null) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (onSeek != null) 28.dp else 4.dp)
            .then(
                if (onSeek != null) {
                    Modifier.pointerInput(onSeek) {
                        detectTapGestures { offset ->
                            val fraction = (offset.x / size.width).coerceIn(0f, 1f)
                            onSeek(fraction)
                        }
                    }
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.16f)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(4.dp)
                    .background(TonezenTeal),
            )
        }
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
