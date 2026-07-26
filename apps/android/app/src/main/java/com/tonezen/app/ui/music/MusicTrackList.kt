package com.tonezen.app.ui.music

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tonezen.app.playback.MusicDownloadState
import com.tonezen.app.ui.components.PlayButton
import com.tonezen.app.ui.components.TonezenTrackListRow
import com.tonezen.app.ui.components.TrackDownloadButton
import com.tonezen.app.ui.components.TrackDownloadedIndicator
import com.tonezen.app.ui.components.TrackRowOverflowMenu
import com.tonezen.app.ui.theme.TonezenBorder
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun MusicWaveCard(
    tracks: List<MusicListTrack>,
    musicPlayback: MusicPlaybackUi,
    isNetworkOnline: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playableCount = tracks.count { it.isDownloaded || isNetworkOnline }
    val fallbackTrack = tracks.firstOrNull()
    val title = musicPlayback.trackTitle ?: fallbackTrack?.trackTitle ?: "Запустить волну"
    val subtitle = musicPlayback.artist ?: fallbackTrack?.artist
    val seed = musicPlayback.trackId ?: fallbackTrack?.trackId ?: "music-wave"

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .border(BorderStroke(1.dp, TonezenBorder.copy(alpha = 0.55f)), RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SpectrumCoverArt(
            seed = seed,
            isPlaying = musicPlayback.isPlaying,
            cornerRadius = 16,
            modifier = Modifier.size(84.dp),
        )
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "Моя волна",
                color = TonezenTeal,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = title,
                color = TonezenInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle ?: "${playableCount} треков доступно",
                color = TonezenMuted,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = "${playableCount} треков доступно",
                    color = TonezenMuted.copy(alpha = 0.72f),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
        PlayButton(
            isPlaying = musicPlayback.isPlaying,
            onClick = onClick,
            modifier = Modifier.size(58.dp),
        )
    }
}

@Composable
internal fun MusicAllTracksToggle(
    count: Int,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Все треки",
            color = TonezenInk,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "${if (expanded) "-" else "+"} $count",
            color = TonezenMuted,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

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

    val isBulkActive = musicDownload.isBulkDownloading
    val progress = musicDownload.bulkProgress ?: (downloaded.toFloat() / displayTotal.toFloat())
    val label = if (isBulkActive) "Остановить загрузку" else "Скачать все"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.04f))
            .border(BorderStroke(1.dp, TonezenBorder.copy(alpha = 0.5f)), RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                color = TonezenInk,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${downloaded} из ${displayTotal}",
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
    isQueued: Boolean,
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
        clickEnabled = true,
        modifier = modifier,
        trailing = {
            when {
                isDownloading || isQueued -> {
                    TrackDownloadButton(
                        progress = downloadProgress,
                        onClick = onDownloadClick,
                        enabled = true,
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
                enabled = true,
                showDelete = track.isDownloaded,
                deleteLabel = "Удалить загрузку",
            )
        },
    )
}
