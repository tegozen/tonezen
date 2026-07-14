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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
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
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
import com.tonezen.app.ui.components.TonezenTrackListRow
import com.tonezen.app.ui.components.TrackDownloadButton
import com.tonezen.app.ui.components.TrackDownloadedIndicator
import com.tonezen.app.ui.components.TrackRowOverflowMenu
import com.tonezen.app.playback.DownloadQueueState
import com.tonezen.app.ui.theme.TonezenAmber
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenMuted
import com.tonezen.app.ui.theme.TonezenTeal
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import com.tonezen.app.ui.components.TonezenGlassAlertDialog
import androidx.compose.material3.TextButton
import com.tonezen.app.ui.components.PlayButton
import com.tonezen.app.ui.components.ProgressBar
import com.tonezen.app.ui.components.RoundControl
import com.tonezen.app.ui.components.animateItemAboveBottomPadding
import com.tonezen.app.ui.theme.durationLabel
import dev.chrisbanes.haze.HazeState

@Composable
internal fun BookDetailScreen(
    padding: PaddingValues,
    hazeState: HazeState,
    @Suppress("UNUSED_PARAMETER") book: Book,
    uiState: BookDetailUiState,
    onBack: () -> Unit,
    onTrackClick: (Track) -> Unit,
    onMarkTrackListened: (Track) -> Unit,
    onMarkTrackUnlistened: (Track) -> Unit,
    onRemoveTrackDownload: (Track) -> Unit,
    onDownloadTrack: (Track) -> Unit,
    onDownloadBook: () -> Unit,
    onToggleBookListened: () -> Unit,
    onRemoveBookDownloads: () -> Unit,
    onContinueListening: () -> Unit,
    onPlaybackPlayPause: () -> Unit,
    onPlaybackSeekBy: (Long) -> Unit,
    onPlaybackSeekToFraction: (Float) -> Unit,
    onDismissPlaybackError: () -> Unit,
    onDismissDownloadError: () -> Unit,
    onConfirmEarlierChapter: () -> Unit,
    onDismissEarlierChapter: () -> Unit,
    bottomScrollPadding: Dp,
) {
    val tracks = uiState.tracks
    val activeTrackId = uiState.activeTrackId
    val sortedTracks = bookDetailTracksForDisplay(tracks)
    val showDownload = tracks.any { it.localPath.isNullOrBlank() }
    val showRemoveDownload = tracks.any { !it.localPath.isNullOrBlank() }
    val isBookFullyDownloaded = tracks.all { !it.localPath.isNullOrBlank() }
    val activeTrack = sortedTracks.find { it.id == activeTrackId }
    val playbackTrack = activeTrack?.takeIf { uiState.isPlaybackActiveForBook }
    val isBookListened = isBookFullyListened(sortedTracks, uiState.audiobookProgress)
    val continueState = canContinueBookListening(
        bookId = book.id,
        tracks = sortedTracks,
        progress = uiState.audiobookProgress,
    )
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val density = LocalDensity.current
    val hasPlaybackControls = playbackTrack != null
    // Показываем кнопку "Продолжить" / "Начать слушать" только если:
    // 1. Книга не полностью прослушана И
    // 2. Книга сейчас НЕ играет (когда книга играет, плеер уже доступен внутри активного трека).
    val hasContinueButton = !isBookListened && !hasPlaybackControls
    val playbackErrorMessage = uiState.playbackErrorMessage
    val downloadErrorMessage = when (uiState.error) {
        BookDetailViewModel.DOWNLOAD_FAILED_ERROR -> "Не удалось скачать трек"
        BookDetailViewModel.DOWNLOAD_OFFLINE_ERROR -> "Нет сети"
        else -> null
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

    LaunchedEffect(activeTrackId, sortedTracks, hasPlaybackControls, hasContinueButton) {
        val trackId = activeTrackId ?: return@LaunchedEffect
        val trackIndex = sortedTracks.indexOfFirst { it.id == trackId }
        if (trackIndex < 0) return@LaunchedEffect
        val listIndex = bookDetailTrackListIndex(
            trackIndex = trackIndex,
            hasPlaybackControls = hasPlaybackControls,
            hasContinueButton = hasContinueButton,
        )
        val bottomPaddingPx = with(density) { bottomScrollPadding.roundToPx() }
        listState.animateItemAboveBottomPadding(listIndex, bottomPaddingPx)
    }

    BackHandler(onBack = onBack)

    Box(modifier = Modifier.fillMaxSize()) {
    TonezenFixedHeaderScreen(
        hazeState = hazeState,
        padding = padding,
        onBack = onBack,
        bottomScrollPadding = bottomScrollPadding,
        listState = listState,
        title = {
            Text(
                text = "Главы",
                color = TonezenInk,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        },
        trailing = {
            DetailHeaderOverflowMenu(
                showDownload = showDownload,
                isDownloaded = isBookFullyDownloaded,
                showRemoveDownload = showRemoveDownload,
                isListened = isBookListened,
                onDownload = onDownloadBook,
                onToggleListened = onToggleBookListened,
                onRemoveDownloads = onRemoveBookDownloads,
            )
        },
    ) {
        // Кнопка "Продолжить" / "Начать слушать" — показывается когда книга не играет и не прослушана.
        if (hasPlaybackControls && playbackTrack != null) {
            item(key = "playback-controls") {
                BookDetailPlaybackControls(
                    track = playbackTrack,
                    positionMs = uiState.playbackPositionMs,
                    durationMs = uiState.playbackDurationMs,
                    isPlaying = uiState.isPlaying,
                    onPlayPause = onPlaybackPlayPause,
                    onSeekBy = onPlaybackSeekBy,
                    onSeekToFraction = onPlaybackSeekToFraction,
                )
            }
        }
        if (hasContinueButton) {
            item(key = "continue-listening") {
                Button(
                    onClick = onContinueListening,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    if (continueState != null) {
                        ContinueResumeMeta(
                            state = continueState,
                            variant = ContinueResumeVariant.Button,
                        )
                    } else {
                        Text("Воспроизвести")
                    }
                }
            }
        }
        items(sortedTracks, key = { it.id }) { track ->
            ChapterTrackRow(
                track = track,
                sortedTracks = sortedTracks,
                audiobookProgress = uiState.audiobookProgress,
                isActive = track.id == activeTrackId,
                livePositionMs = if (track.id == activeTrackId) uiState.playbackPositionMs else null,
                downloadQueueState = uiState.downloadQueueState,
                onClick = { onTrackClick(track) },
                onMarkTrackListened = { onMarkTrackListened(track) },
                onMarkTrackUnlistened = { onMarkTrackUnlistened(track) },
                onRemoveDownload = { onRemoveTrackDownload(track) },
                onDownloadTrack = { onDownloadTrack(track) },
            )
        }
    }
    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = bottomScrollPadding),
    )
    EarlierChapterConfirmDialog(
        visible = uiState.confirmEarlierChapter != null,
        hazeState = hazeState,
        onDismiss = onDismissEarlierChapter,
        onConfirm = onConfirmEarlierChapter,
    )
    }
}

@Composable
private fun EarlierChapterConfirmDialog(
    visible: Boolean,
    hazeState: HazeState,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    TonezenGlassAlertDialog(
        visible = visible,
        hazeState = hazeState,
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Начать с этой главы?",
                color = TonezenInk,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            Text(
                "Вы уже слушали более позднюю главу. Начать выбранную главу с начала?",
                color = TonezenMuted,
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Начать", color = TonezenTeal)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена")
            }
        },
    )
}

@Composable
private fun BookDetailPlaybackControls(
    track: Track,
    positionMs: Long,
    durationMs: Long,
    isPlaying: Boolean,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekToFraction: (Float) -> Unit,
) {
    val progress = if (durationMs > 0L) {
        positionMs.toFloat() / durationMs.toFloat()
    } else {
        0f
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = track.title,
            color = TonezenInk,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        ProgressBar(
            progress = progress,
            onSeek = onSeekToFraction,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                durationLabel(positionMs),
                color = TonezenMuted,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                durationLabel(durationMs),
                color = TonezenMuted,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundControl(
                label = "-15",
                outlined = true,
                size = 40.dp,
            ) {
                onSeekBy(-15_000L)
            }
            PlayButton(
                isPlaying = isPlaying,
                modifier = Modifier.size(56.dp),
                onClick = onPlayPause,
            )
            RoundControl(
                label = "+15",
                outlined = true,
                size = 40.dp,
            ) {
                onSeekBy(15_000L)
            }
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
