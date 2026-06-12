package com.tonezen.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonezen.app.R
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.progress.TrackListenStatus
import com.tonezen.app.domain.progress.resolveTrackListenState
import com.tonezen.app.ui.components.DownloadConfirmSheet
import com.tonezen.app.ui.components.PlayingBars
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
import com.tonezen.app.ui.components.TonezenTrackListRow
import com.tonezen.app.ui.components.TrackDownloadedIndicator
import com.tonezen.app.ui.components.TrackRowOverflowMenu
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.items
import dev.chrisbanes.haze.HazeState

@Composable
internal fun BookDetailScreen(
    padding: PaddingValues,
    hazeState: HazeState,
    @Suppress("UNUSED_PARAMETER") book: Book,
    uiState: BookDetailUiState,
    onBack: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onConfirmDownload: () -> Unit,
    onDismissDownloadSheet: () -> Unit,
    onMarkTrackListened: (Track) -> Unit,
    onRemoveTrackDownload: (Track) -> Unit,
    bottomScrollPadding: Dp,
) {
    val tracks = uiState.tracks
    val activeTrackId = uiState.activeTrackId
    val sortedTracks = tracks.sortedBy { it.sortOrder }

    BackHandler {
        when {
            uiState.showDownloadSheet -> onDismissDownloadSheet()
            else -> onBack()
        }
    }

    DownloadConfirmSheet(
        visible = uiState.showDownloadSheet,
        hazeState = hazeState,
        estimatedBytes = uiState.estimatedDownloadBytes,
        onDismiss = onDismissDownloadSheet,
        onConfirm = onConfirmDownload,
    )

    TonezenFixedHeaderScreen(
        hazeState = hazeState,
        padding = padding,
        onBack = onBack,
        bottomScrollPadding = bottomScrollPadding,
        title = {
            Text(
                text = stringResource(R.string.chapters),
                color = TonezenInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
    ) {
        items(tracks, key = { it.id }) { track ->
            ChapterTrackRow(
                track = track,
                sortedTracks = sortedTracks,
                audiobookProgress = uiState.audiobookProgress,
                isActive = track.id == activeTrackId,
                livePositionMs = if (track.id == activeTrackId) uiState.playbackPositionMs else null,
                onClick = { onTrackClick(track) },
                onMarkListened = { onMarkTrackListened(track) },
                onRemoveDownload = { onRemoveTrackDownload(track) },
            )
        }
    }
}

@Composable
internal fun ChapterTrackRow(
    track: Track,
    sortedTracks: List<Track>,
    audiobookProgress: AudiobookProgress?,
    isActive: Boolean,
    livePositionMs: Long?,
    onClick: () -> Unit,
    onMarkListened: () -> Unit,
    onRemoveDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val listenState = resolveTrackListenState(
        sortedTracks = sortedTracks,
        bookProgress = audiobookProgress,
        trackId = track.id,
        livePositionMs = livePositionMs,
    )
    val isDownloaded = !track.localPath.isNullOrBlank()
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
            when {
                isActive -> PlayingBars(active = true)
                listenPercent != null -> {
                    Text(
                        text = stringResource(R.string.cycle_listen_progress, listenPercent),
                        color = TonezenTeal,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
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
            if (isDownloaded) {
                TrackDownloadedIndicator()
            }
            TrackRowOverflowMenu(
                onDelete = onRemoveDownload,
                deleteLabelRes = R.string.remove_download,
                showDelete = isDownloaded,
                onMarkListened = onMarkListened,
            )
        },
    )
}
