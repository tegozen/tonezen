package com.tonezen.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.tonezen.app.domain.progress.canContinueBookListening
import com.tonezen.app.domain.progress.isBookFullyListened
import com.tonezen.app.domain.progress.resolveTrackListenState
import com.tonezen.app.ui.components.ContinueResumeMeta
import com.tonezen.app.ui.components.ContinueResumeVariant
import com.tonezen.app.ui.components.DetailHeaderOverflowMenu
import com.tonezen.app.ui.components.DownloadConfirmSheet
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
import com.tonezen.app.ui.components.TonezenTrackListRow
import com.tonezen.app.ui.components.TrackDownloadedIndicator
import com.tonezen.app.ui.components.TrackRowOverflowMenu
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.ui.Alignment
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
    onMarkTrackUnlistened: (Track) -> Unit,
    onRemoveTrackDownload: (Track) -> Unit,
    onDownloadBook: () -> Unit,
    onToggleBookListened: () -> Unit,
    onRemoveBookDownloads: () -> Unit,
    onContinueListening: () -> Unit,
    onDismissPlaybackError: () -> Unit,
    onDismissDownloadError: () -> Unit,
    bottomScrollPadding: Dp,
) {
    val tracks = uiState.tracks
    val activeTrackId = uiState.activeTrackId
    val sortedTracks = tracks.sortedBy { it.sortOrder }
    val showDownload = tracks.any { it.localPath.isNullOrBlank() }
    val showRemoveDownload = tracks.any { !it.localPath.isNullOrBlank() }
    val isBookListened = isBookFullyListened(sortedTracks, uiState.audiobookProgress)
    val continueState = canContinueBookListening(
        bookId = book.id,
        tracks = sortedTracks,
        progress = uiState.audiobookProgress,
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val playbackErrorMessage = uiState.playbackErrorRes?.let { stringResource(it) }
    val downloadErrorMessage = if (uiState.error == BookDetailViewModel.DOWNLOAD_FAILED_ERROR) {
        stringResource(R.string.music_playback_error_download)
    } else {
        null
    }

    LaunchedEffect(playbackErrorMessage) {
        playbackErrorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onDismissPlaybackError()
        }
    }

    LaunchedEffect(downloadErrorMessage) {
        downloadErrorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onDismissDownloadError()
        }
    }

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

    Box(modifier = Modifier.fillMaxSize()) {
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
        trailing = {
            DetailHeaderOverflowMenu(
                showDownload = showDownload,
                showRemoveDownload = showRemoveDownload,
                isListened = isBookListened,
                onDownload = onDownloadBook,
                onToggleListened = onToggleBookListened,
                onRemoveDownloads = onRemoveBookDownloads,
            )
        },
    ) {
        continueState?.takeIf { !isBookListened }?.let { state ->
            item(key = "continue-listening") {
                Button(
                    onClick = onContinueListening,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    ContinueResumeMeta(
                        state = state,
                        variant = ContinueResumeVariant.Button,
                    )
                }
            }
        }
        items(tracks, key = { it.id }) { track ->
            ChapterTrackRow(
                track = track,
                sortedTracks = sortedTracks,
                audiobookProgress = uiState.audiobookProgress,
                isActive = track.id == activeTrackId,
                livePositionMs = if (track.id == activeTrackId) uiState.playbackPositionMs else null,
                onClick = { onTrackClick(track) },
                onMarkTrackListened = { onMarkTrackListened(track) },
                onMarkTrackUnlistened = { onMarkTrackUnlistened(track) },
                onRemoveDownload = { onRemoveTrackDownload(track) },
            )
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottomScrollPadding),
    )
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
    onMarkTrackListened: () -> Unit,
    onMarkTrackUnlistened: () -> Unit,
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
            if (listenPercent != null) {
                Text(
                    text = stringResource(R.string.cycle_listen_progress, listenPercent),
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
            if (isDownloaded) {
                TrackDownloadedIndicator()
            }
            TrackRowOverflowMenu(
                onDelete = onRemoveDownload,
                deleteLabelRes = R.string.remove_download,
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
