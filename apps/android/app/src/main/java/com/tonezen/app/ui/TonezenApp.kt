package com.tonezen.app.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.ui.auth.AuthScreen
import com.tonezen.app.ui.auth.AuthViewModel
import com.tonezen.app.ui.library.LibraryScreen
import com.tonezen.app.ui.library.LibraryViewModel
import com.tonezen.app.ui.player.BookDetailScreen
import com.tonezen.app.ui.player.BookDetailViewModel
import com.tonezen.app.ui.theme.TonezenTheme

@Composable
fun TonezenApp(
    authViewModel: AuthViewModel = hiltViewModel(),
    libraryViewModel: LibraryViewModel = hiltViewModel(),
) {
    val libraryState by libraryViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val selectedBook = libraryState.selectedBook

    TonezenTheme {
        when {
            libraryState.sessionState == SessionState.UNAUTHENTICATED -> AuthScreen(
                padding = PaddingValues(0.dp),
                onLogin = authViewModel::login,
                error = authState.error,
            )

            selectedBook == null -> LibraryScreen(
                books = libraryState.books,
                downloadedBookIds = libraryState.downloadedBookIds,
                offlineBanner = libraryState.sessionState == SessionState.AUTHENTICATED_OFFLINE,
                nowPlayingTitle = libraryState.nowPlayingTitle,
                onBookClick = libraryViewModel::selectBook,
                onLogout = libraryViewModel::logout,
            )

            else -> {
                val bookDetailViewModel: BookDetailViewModel = hiltViewModel(key = selectedBook.id)
                val detailState by bookDetailViewModel.uiState.collectAsState()

                LaunchedEffect(selectedBook.id) {
                    bookDetailViewModel.loadBook(selectedBook)
                }

                BookDetailScreen(
                    book = selectedBook,
                    tracks = detailState.tracks,
                    progressTrackTitle = detailState.progressTrackTitle,
                    nowPlayingTitle = detailState.nowPlayingTitle,
                    isPlaying = detailState.isPlaying,
                    downloadProgress = detailState.downloadProgress,
                    onPlay = bookDetailViewModel::playBook,
                    onPause = bookDetailViewModel::pausePlayback,
                    onResume = bookDetailViewModel::resumePlayback,
                    onDownload = bookDetailViewModel::downloadBook,
                    onDeleteLocal = bookDetailViewModel::deleteLocalDownloads,
                    onBack = libraryViewModel::clearSelection,
                    onToggleFavorite = bookDetailViewModel::toggleFavorite,
                )
            }
        }
    }
}
