package com.tonezen.app.ui.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonezen.app.domain.model.Book
import com.tonezen.app.ui.player.BookDetailScreen
import com.tonezen.app.ui.player.BookDetailViewModel
import dev.chrisbanes.haze.HazeState

@Composable
internal fun AppShellBookDetailRoute(
    book: Book,
    shellState: AppShellUiState,
    shellViewModel: AppShellViewModel,
    hazeState: HazeState,
    overlayBottomScrollPadding: Dp,
) {
    val bookDetailViewModel: BookDetailViewModel = hiltViewModel(key = book.id)
    val detailState by bookDetailViewModel.uiState.collectAsStateWithLifecycle()
    val playbackProgress by bookDetailViewModel.playbackProgress.collectAsStateWithLifecycle()
    val trackDownloads by bookDetailViewModel.trackDownloads.collectAsStateWithLifecycle()

    LaunchedEffect(book.id) {
        bookDetailViewModel.loadBook(book)
    }

    LaunchedEffect(shellState.autoResumeBookId, book.id, detailState.book?.id) {
        if (shellState.autoResumeBookId == book.id && detailState.book?.id == book.id) {
            shellViewModel.consumeAutoResumeBook(book.id)
            bookDetailViewModel.continueListening()
        }
    }

    BookDetailScreen(
        padding = PaddingValues(0.dp),
        hazeState = hazeState,
        book = book,
        uiState = detailState,
        playbackProgress = playbackProgress,
        trackDownloads = trackDownloads,
        onBack = shellViewModel::closeBook,
        onTrackClick = bookDetailViewModel::playTrack,
        onMarkTrackListened = bookDetailViewModel::markTrackListened,
        onMarkTrackUnlistened = bookDetailViewModel::markTrackUnlistened,
        onRemoveTrackDownload = bookDetailViewModel::removeTrackDownload,
        onDownloadTrack = bookDetailViewModel::requestTrackDownload,
        onDownloadBook = bookDetailViewModel::requestDownload,
        onToggleBookListened = bookDetailViewModel::toggleBookListened,
        onRemoveBookDownloads = bookDetailViewModel::deleteLocalDownloads,
        onContinueListening = bookDetailViewModel::continueListening,
        onPlaybackPlayPause = bookDetailViewModel::pauseOrResume,
        onPlaybackSeekBy = bookDetailViewModel::seekBy,
        onPlaybackSeekToFraction = bookDetailViewModel::seekToFraction,
        onDismissPlaybackError = bookDetailViewModel::clearPlaybackError,
        onDismissDownloadError = bookDetailViewModel::clearDownloadError,
        onConfirmEarlierChapter = bookDetailViewModel::confirmEarlierChapterPlayback,
        onDismissEarlierChapter = bookDetailViewModel::dismissEarlierChapterPrompt,
        onChooseProgressSyncLocal = bookDetailViewModel::chooseProgressSyncLocal,
        onChooseProgressSyncServer = bookDetailViewModel::chooseProgressSyncServer,
        onDismissProgressSyncConflict = bookDetailViewModel::dismissProgressSyncConflictPrompt,
        bottomScrollPadding = overlayBottomScrollPadding,
    )
}
