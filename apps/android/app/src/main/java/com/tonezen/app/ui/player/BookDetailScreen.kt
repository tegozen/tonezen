package com.tonezen.app.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.progress.canContinueBookListening
import com.tonezen.app.domain.progress.isBookFullyListened
import com.tonezen.app.ui.components.ContinueResumeMeta
import com.tonezen.app.ui.components.ContinueResumeVariant
import com.tonezen.app.ui.components.DetailHeaderOverflowMenu
import com.tonezen.app.ui.components.TonezenFixedHeaderScreen
import com.tonezen.app.ui.components.animateItemAboveBottomPadding
import com.tonezen.app.ui.theme.TonezenInk
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
    val downloadErrorMessage = bookDetailDownloadErrorMessage(uiState.error)

    BookDetailErrorSnackbars(
        playbackErrorMessage = playbackErrorMessage,
        downloadErrorMessage = downloadErrorMessage,
        snackbarHostState = snackbarHostState,
        onDismissPlaybackError = onDismissPlaybackError,
        onDismissDownloadError = onDismissDownloadError,
    )

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
