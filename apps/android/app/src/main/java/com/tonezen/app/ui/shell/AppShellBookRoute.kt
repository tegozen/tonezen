package com.tonezen.app.ui.shell

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
    hazeState: HazeState,
    overlayBottomScrollPadding: Dp,
    autoResume: Boolean,
    onBack: () -> Unit,
) {
    val bookDetailViewModel: BookDetailViewModel = hiltViewModel(key = book.id)
    val detailState by bookDetailViewModel.uiState.collectAsStateWithLifecycle()
    val playbackProgress by bookDetailViewModel.playbackProgress.collectAsStateWithLifecycle()
    val trackDownloads by bookDetailViewModel.trackDownloads.collectAsStateWithLifecycle()
    var autoResumeConsumed by rememberSaveable(book.id, autoResume) { mutableStateOf(false) }

    LaunchedEffect(book.id) {
        bookDetailViewModel.loadBook(book)
    }

    LaunchedEffect(autoResume, detailState.book?.id) {
        if (autoResume && !autoResumeConsumed && detailState.book?.id == book.id) {
            autoResumeConsumed = true
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
        onBack = onBack,
        onTrackClick = bookDetailViewModel::playTrack,
        onMarkTrackListened = bookDetailViewModel::markTrackListened,
        onMarkTrackUnlistened = bookDetailViewModel::markTrackUnlistened,
        onRemoveTrackDownload = bookDetailViewModel::removeTrackDownload,
        onDownloadTrack = bookDetailViewModel::requestTrackDownload,
        onDownloadBook = bookDetailViewModel::requestDownload,
        onToggleBookListened = bookDetailViewModel::toggleBookListened,
        onRemoveBookDownloads = bookDetailViewModel::deleteLocalDownloads,
        onContinueListening = bookDetailViewModel::continueListening,
        onDismissPlaybackError = bookDetailViewModel::clearPlaybackError,
        onDismissDownloadError = bookDetailViewModel::clearDownloadError,
        onConfirmEarlierChapter = bookDetailViewModel::confirmEarlierChapterPlayback,
        onDismissEarlierChapter = bookDetailViewModel::dismissEarlierChapterPrompt,
        onConfirmEarlierCycleBook = bookDetailViewModel::confirmEarlierCycleBookPlayback,
        onDismissEarlierCycleBook = bookDetailViewModel::dismissEarlierCycleBookPrompt,
        onChooseProgressSyncLocal = bookDetailViewModel::chooseProgressSyncLocal,
        onChooseProgressSyncServer = bookDetailViewModel::chooseProgressSyncServer,
        onDismissProgressSyncConflict = bookDetailViewModel::dismissProgressSyncConflictPrompt,
        bottomScrollPadding = overlayBottomScrollPadding,
    )
}
