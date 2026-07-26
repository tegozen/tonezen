package com.tonezen.app.ui.player

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.progress.TrackListenStatus
import com.tonezen.app.domain.progress.resolveTrackListenState
import com.tonezen.app.playback.DownloadQueueState
import com.tonezen.app.ui.components.TonezenTrackListRow
import com.tonezen.app.ui.components.TrackDownloadButton
import com.tonezen.app.ui.components.TrackDownloadedIndicator
import com.tonezen.app.ui.components.TrackRowOverflowMenu
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal

@Composable
internal fun ChapterTrackRow(
    track: Track,
    sortedTracks: List<Track>,
    audiobookProgress: AudiobookProgress?,
    isActive: Boolean,
    livePositionMs: Long?,
    downloadQueueState: DownloadQueueState,
    onClick: () -> Unit,
    onMarkTrackListened: () -> Unit,
    onMarkTrackUnlistened: () -> Unit,
    onRemoveDownload: () -> Unit,
    onDownloadTrack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listenState = resolveTrackListenState(
        sortedTracks = sortedTracks,
        bookProgress = audiobookProgress,
        trackId = track.id,
        livePositionMs = livePositionMs,
    )
    val isDownloaded = !track.localPath.isNullOrBlank()
    val isDownloading = downloadQueueState.progressForTrack(track.id) != null
    val isQueued = downloadQueueState.isTrackQueued(track.id)
    val downloadProgress = downloadQueueState.progressForTrack(track.id)
    val listenPercent = when (listenState.status) {
        TrackListenStatus.COMPLETED -> 100
        TrackListenStatus.IN_PROGRESS -> (listenState.fraction * 100).toInt().coerceIn(1, 99)
        TrackListenStatus.NOT_STARTED -> null
    }
    TonezenTrackListRow(
        title = track.title,
        durationMs = track.durationMs,
        isActive = isActive,
        listenProgress = listenState.barFraction,
        onClick = onClick,
        modifier = modifier,
        leading = {
            if (listenPercent != null) {
                Text(
                    text = "${listenPercent}%",
                    color = TonezenTeal,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(
                (track.sortOrder + 1).toString(),
                color = when {
                    isActive -> TonezenAmber
                    listenPercent != null -> TonezenTeal
                    else -> TonezenMuted
                },
            )
        },
        trailing = {
            when {
                isDownloading || isQueued -> {
                    TrackDownloadButton(
                        progress = downloadProgress,
                        onClick = onDownloadTrack,
                    )
                }
                isDownloaded -> TrackDownloadedIndicator()
                else -> {
                    TrackDownloadButton(
                        progress = null,
                        onClick = onDownloadTrack,
                    )
                }
            }
            TrackRowOverflowMenu(
                onDelete = onRemoveDownload,
                deleteLabel = "Удалить загрузку",
                showDelete = isDownloaded,
                onToggleListened = {
                    if (listenState.status == TrackListenStatus.COMPLETED) {
                        onMarkTrackUnlistened()
                    } else {
                        onMarkTrackListened()
                    }
                },
                isListened = listenState.status == TrackListenStatus.COMPLETED,
            )
        },
    )
}
