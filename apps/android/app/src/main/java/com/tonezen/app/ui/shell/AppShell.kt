package com.tonezen.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.ui.components.BottomDestination
import com.tonezen.app.ui.components.MiniPlayer
import com.tonezen.app.ui.components.TonezenBottomNavigation
import com.tonezen.app.ui.downloads.DownloadsScreen
import com.tonezen.app.ui.downloads.DownloadsViewModel
import com.tonezen.app.ui.library.LibraryScreen
import com.tonezen.app.ui.library.LibraryViewModel
import com.tonezen.app.ui.player.BookDetailScreen
import com.tonezen.app.ui.player.BookDetailViewModel
import com.tonezen.app.ui.player.NowPlayingSheet
import com.tonezen.app.ui.profile.ProfileScreen
import com.tonezen.app.ui.profile.ProfileViewModel
import com.tonezen.app.ui.theme.TonezenAppBg

@Composable
fun AppShell(
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    shellViewModel: AppShellViewModel = hiltViewModel(),
    downloadsViewModel: DownloadsViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
) {
    val libraryState by libraryViewModel.uiState.collectAsState()
    val shellState by shellViewModel.uiState.collectAsState()
    val selectedBook = shellState.selectedBook

    if (selectedBook != null) {
        val bookDetailViewModel: BookDetailViewModel = hiltViewModel(key = selectedBook.id)
        val detailState by bookDetailViewModel.uiState.collectAsState()

        LaunchedEffect(selectedBook.id) {
            bookDetailViewModel.loadBook(selectedBook)
        }

        BookDetailScreen(
            book = selectedBook,
            uiState = detailState,
            onPlay = bookDetailViewModel::playBook,
            onPause = bookDetailViewModel::pausePlayback,
            onResume = bookDetailViewModel::resumePlayback,
            onDownload = bookDetailViewModel::requestDownload,
            onConfirmDownload = bookDetailViewModel::downloadBook,
            onDismissDownloadSheet = bookDetailViewModel::dismissDownloadSheet,
            onDeleteLocal = bookDetailViewModel::deleteLocalDownloads,
            onBack = shellViewModel::closeBook,
            onToggleFavorite = bookDetailViewModel::toggleFavorite,
            onSelectTab = bookDetailViewModel::selectTab,
            onSeek = bookDetailViewModel::seekTo,
            onSeekBy = bookDetailViewModel::seekBy,
            onCycleSpeed = bookDetailViewModel::cycleSpeed,
            onTrackClick = bookDetailViewModel::playTrack,
            onShowTrackActions = bookDetailViewModel::showTrackActions,
            onDismissTrackActions = bookDetailViewModel::dismissTrackActions,
            onMarkComplete = bookDetailViewModel::markTrackComplete,
            onPlayNext = bookDetailViewModel::playNextTrack,
            onRemoveDownload = bookDetailViewModel::removeTrackDownload,
        )
        return
    }

    Scaffold(
        containerColor = TonezenAppBg,
        bottomBar = {
            if (!shellState.showExpandedPlayer) {
                Column {
                    MiniPlayer(
                        title = shellState.nowPlayingTitle,
                        subtitle = shellState.nowPlayingSubtitle,
                        coverSeed = shellState.nowPlayingCoverSeed,
                        enabled = shellState.showMiniPlayer,
                        isPlaying = shellState.isPlaying,
                        positionMs = shellState.positionMs,
                        durationMs = shellState.durationMs,
                        onBarClick = shellViewModel::onMiniPlayerClick,
                        onPlayPauseClick = shellViewModel::onMiniPlayerPlayPause,
                    )
                    TonezenBottomNavigation(
                        selected = shellState.currentTab,
                        onSelect = shellViewModel::selectTab,
                    )
                }
            }
        },
    ) { padding ->
        BackHandler(enabled = shellState.showExpandedPlayer) {
            shellViewModel.dismissExpandedPlayer()
        }

        if (shellState.showExpandedPlayer) {
            NowPlayingSheet(
                shellState = shellState,
                onDismiss = shellViewModel::dismissExpandedPlayer,
            )
        }

        when (shellState.currentTab) {
            BottomDestination.Library -> LibraryScreen(
                padding = padding,
                books = libraryViewModel.filteredBooks,
                allBooks = libraryState.books,
                downloadedBookIds = libraryState.downloadedBookIds,
                favoriteBookIds = libraryState.favoriteBookIds,
                offlineBanner = libraryState.sessionState == SessionState.AUTHENTICATED_OFFLINE,
                isRefreshing = libraryState.isRefreshing,
                filter = libraryState.filter,
                showFilterSheet = libraryState.showFilterSheet,
                onBookClick = { book ->
                    shellViewModel.openBook(book)
                },
                onSearchChange = libraryViewModel::setSearchQuery,
                onFilterClick = { libraryViewModel.setFilterSheetVisible(true) },
                onDismissFilterSheet = { libraryViewModel.setFilterSheetVisible(false) },
                onApplyFilter = libraryViewModel::applyFilter,
                onResetFilter = libraryViewModel::resetFilter,
                onContentFilterChange = libraryViewModel::setContentFilter,
                onSortOrderChange = libraryViewModel::setSortOrder,
                onRefresh = libraryViewModel::refresh,
                musicPreview = libraryState.musicPreview,
                musicPlayback = libraryState.musicPlayback,
                musicDownloadProgress = libraryState.musicDownloadProgress,
                musicPlaybackErrorRes = libraryState.musicPlaybackErrorRes,
                onMusicPlayPause = libraryViewModel::toggleMusicPlayback,
                onMusicShuffle = libraryViewModel::shuffleMusicPreview,
                onMusicTabSelected = libraryViewModel::onMusicTabSelected,
            )

            BottomDestination.Downloads -> DownloadsScreen(
                padding = padding,
                viewModel = downloadsViewModel,
            )

            BottomDestination.Profile -> ProfileScreen(
                padding = padding,
                viewModel = profileViewModel,
                onOpenDownloads = { shellViewModel.selectTab(BottomDestination.Downloads) },
            )
        }
    }
}
