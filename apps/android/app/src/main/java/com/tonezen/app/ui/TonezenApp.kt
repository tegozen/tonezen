package com.tonezen.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.ui.auth.AuthScreen
import com.tonezen.app.ui.library.LibraryScreen
import com.tonezen.app.ui.player.BookDetailScreen
import com.tonezen.app.ui.theme.TonezenTheme

@Composable
fun TonezenApp(viewModel: MainViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsState()

    TonezenTheme {
        when {
            state.sessionState == SessionState.UNAUTHENTICATED -> AuthScreen(
                padding = PaddingValues(0.dp),
                onLogin = viewModel::login,
                error = state.error,
            )

            state.selectedBook == null -> LibraryScreen(
                books = state.books,
                downloadedBookIds = state.downloadedBookIds,
                offlineBanner = state.sessionState == SessionState.AUTHENTICATED_OFFLINE,
                nowPlayingTitle = state.nowPlayingTitle,
                onBookClick = viewModel::selectBook,
                onLogout = viewModel::logout,
            )

            else -> BookDetailScreen(
                book = state.selectedBook!!,
                tracks = state.tracks,
                progressLabel = state.progressLabel,
                nowPlayingTitle = state.nowPlayingTitle,
                isPlaying = state.isPlaying,
                downloadProgress = state.downloadProgress,
                onPlay = viewModel::playBook,
                onPause = viewModel::pausePlayback,
                onResume = viewModel::resumePlayback,
                onDownload = viewModel::downloadBook,
                onDeleteLocal = viewModel::deleteLocalDownloads,
                onBack = viewModel::clearSelection,
                onToggleFavorite = viewModel::toggleFavorite,
            )
        }
    }
}
