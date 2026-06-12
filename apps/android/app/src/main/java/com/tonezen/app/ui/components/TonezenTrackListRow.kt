package com.tonezen.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.tonezen.app.R
import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Brush
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenAppBg
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenError
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.durationLabel

@Composable
internal fun TonezenTrackListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    subtitleColor: Color? = null,
    durationMs: Long? = null,
    isActive: Boolean = false,
    listenProgress: Float? = null,
    onClick: () -> Unit,
    clickEnabled: Boolean = true,
    leading: @Composable (RowScope.() -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit,
) {
    val resolvedSubtitleColor = subtitleColor ?: if (isActive) TonezenTeal else TonezenMuted
    val barProgress = listenProgress?.coerceIn(0f, 1f)?.takeIf { it > 0f }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (isActive) TonezenAmber.copy(alpha = 0.08f) else Color.Transparent)
            .border(
                BorderStroke(
                    1.dp,
                    if (isActive) TonezenAmber.copy(alpha = 0.18f) else TonezenBorder.copy(alpha = 0.35f),
                ),
                RoundedCornerShape(10.dp),
            ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = clickEnabled, onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading?.invoke(this)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    title,
                    color = if (isActive) TonezenAmber else TonezenInk,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        color = resolvedSubtitleColor,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    durationLabel(durationMs),
                    color = TonezenMuted,
                    style = MaterialTheme.typography.bodySmall,
                )
                trailing()
            }
        }
        if (barProgress != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .background(TonezenBorder.copy(alpha = 0.25f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(barProgress)
                        .fillMaxHeight()
                        .background(TonezenTeal),
                )
            }
        }
    }
}

@Composable
internal fun TrackRowOverflowMenu(
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    @StringRes deleteLabelRes: Int = R.string.music_delete_track,
    showDelete: Boolean = true,
    onToggleListened: (() -> Unit)? = null,
    isListened: Boolean = false,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled) { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            OverflowGlyph(tint = TonezenMuted)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            onToggleListened?.let { toggleListened ->
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(
                                if (isListened) R.string.mark_not_listened else R.string.mark_complete,
                            ),
                            color = TonezenInk,
                        )
                    },
                    onClick = {
                        expanded = false
                        toggleListened()
                    },
                )
            }
            if (showDelete) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(deleteLabelRes),
                            color = TonezenError,
                        )
                    },
                    onClick = {
                        expanded = false
                        onDelete()
                    },
                )
            }
        }
    }
}

@Composable
internal fun DetailHeaderOverflowMenu(
    showDownload: Boolean,
    showRemoveDownload: Boolean,
    isListened: Boolean,
    onDownload: () -> Unit,
    onToggleListened: () -> Unit,
    onRemoveDownloads: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .clickable(enabled = enabled) { expanded = true },
            contentAlignment = Alignment.Center,
        ) {
            OverflowGlyph(tint = TonezenMuted)
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (showRemoveDownload) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.remove_download),
                            color = TonezenError,
                        )
                    },
                    onClick = {
                        expanded = false
                        onRemoveDownloads()
                    },
                )
            }
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (isListened) R.string.mark_not_listened else R.string.mark_complete,
                        ),
                        color = TonezenInk,
                    )
                },
                onClick = {
                    expanded = false
                    onToggleListened()
                },
            )
            if (showDownload) {
                DropdownMenuItem(
                    text = {
                        Text(
                            stringResource(R.string.offline_action),
                            color = TonezenInk,
                        )
                    },
                    onClick = {
                        expanded = false
                        onDownload()
                    },
                )
            }
        }
    }
}

@Composable
internal fun TrackDownloadButton(
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val isDownloading = progress != null
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
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
        modifier = modifier.size(36.dp),
        contentAlignment = Alignment.Center,
    ) {
        CheckCircleGlyph(tint = TonezenTeal, size = 18.dp)
    }
}
