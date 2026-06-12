package com.tonezen.app.ui.library

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.playback.MusicDownloadState
import com.tonezen.app.ui.components.CheckCircleGlyph
import com.tonezen.app.ui.components.DownloadGlyph
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import com.tonezen.app.ui.theme.durationLabel

@Composable
internal fun MusicDownloadAllButton(
    tracks: List<MusicListTrack>,
    musicDownload: MusicDownloadState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val total = tracks.size
    if (total == 0) return
    val displayTotal = if (musicDownload.bulkTotal > 0) musicDownload.bulkTotal else total
    val downloaded = if (musicDownload.isBulkDownloading) {
        musicDownload.bulkDownloaded
    } else {
        tracks.count { it.isDownloaded }
    }
    if (downloaded >= displayTotal && !musicDownload.isBulkDownloading) return

    val isDownloading = musicDownload.isBulkDownloading
    val progress = musicDownload.bulkProgress ?: (downloaded.toFloat() / displayTotal.toFloat())

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(BorderStroke(1.dp, TonezenBorder.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
            .clickable(enabled = !isDownloading, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.music_download_all),
                color = TonezenInk,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = stringResource(R.string.music_download_all_progress, downloaded, displayTotal),
                color = TonezenMuted,
                style = MaterialTheme.typography.labelSmall,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.12f)),
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
internal fun MusicTrackDownloadButton(
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
internal fun MusicTrackRow(
    track: MusicListTrack,
    isActive: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
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
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .clickable(enabled = !isDownloading, onClick = onClick),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                track.trackTitle,
                color = if (isActive) TonezenAmber else TonezenInk,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artist,
                color = if (isActive) TonezenTeal else TonezenMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                durationLabel(track.durationMs),
                color = TonezenMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            when {
                isDownloading -> {
                    MusicTrackDownloadButton(
                        progress = downloadProgress,
                        onClick = onDownloadClick,
                        enabled = false,
                    )
                }
                track.isDownloaded -> {
                    Box(
                        modifier = Modifier.size(36.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CheckCircleGlyph(tint = TonezenTeal, size = 18.dp)
                    }
                }
                else -> {
                    MusicTrackDownloadButton(
                        progress = null,
                        onClick = onDownloadClick,
                    )
                }
            }
        }
    }
}
