package com.tonezen.app.ui.library

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.playback.MusicDownloadState
import com.tonezen.app.ui.components.TonezenTrackListRow
import com.tonezen.app.ui.components.TrackDownloadButton
import com.tonezen.app.ui.components.TrackDownloadedIndicator
import com.tonezen.app.ui.components.TrackRowOverflowMenu
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal

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
internal fun MusicTrackRow(
    track: MusicListTrack,
    isActive: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float?,
    onClick: () -> Unit,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TonezenTrackListRow(
        title = track.trackTitle,
        subtitle = track.artist,
        durationMs = track.durationMs,
        isActive = isActive,
        onClick = onClick,
        clickEnabled = !isDownloading,
        modifier = modifier,
        trailing = {
            when {
                isDownloading -> {
                    TrackDownloadButton(
                        progress = downloadProgress,
                        onClick = onDownloadClick,
                        enabled = false,
                    )
                }
                track.isDownloaded -> TrackDownloadedIndicator()
                else -> {
                    TrackDownloadButton(
                        progress = null,
                        onClick = onDownloadClick,
                    )
                }
            }
            TrackRowOverflowMenu(
                onDelete = onDeleteClick,
                enabled = !isDownloading,
                showDelete = track.isDownloaded,
                deleteLabelRes = R.string.remove_download,
            )
        },
    )
}
