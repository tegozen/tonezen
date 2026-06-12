package com.tonezen.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.ui.components.BottomDestination
import com.tonezen.app.ui.components.MiniPlayer
import com.tonezen.app.ui.components.TonezenBottomNavigation
import com.tonezen.app.ui.downloads.DownloadsScreen
import com.tonezen.app.ui.downloads.DownloadsViewModel
import com.tonezen.app.ui.library.CycleDetailScreen
import com.tonezen.app.ui.library.LibraryScreen
import com.tonezen.app.ui.library.LibraryViewModel
import com.tonezen.app.ui.player.BookDetailScreen
import com.tonezen.app.ui.player.BookDetailViewModel
import com.tonezen.app.ui.player.NowPlayingSheet
import com.tonezen.app.ui.profile.ProfileScreen
import com.tonezen.app.ui.profile.ProfileViewModel
import com.tonezen.app.ui.theme.TonezenChromeBarBackground
import com.tonezen.app.ui.theme.TonezenSurface

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
    val selectedCycle = shellState.selectedCycle
    val inLibraryOverlay = selectedCycle != null || selectedBook != null

    Scaffold(
        containerColor = TonezenSurface,
        bottomBar = {
            if (!shellState.showExpandedPlayer) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = TonezenChromeBarBackground,
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
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
                        if (!inLibraryOverlay) {
                            TonezenBottomNavigation(
                                selected = shellState.currentTab,
                                onSelect = shellViewModel::selectTab,
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        BackHandler(enabled = shellState.showExpandedPlayer) {
            shellViewModel.dismissExpandedPlayer()
        }
        BackHandler(enabled = selectedBook != null && !shellState.showExpandedPlayer) {
            shellViewModel.closeBook()
        }
        BackHandler(enabled = selectedBook == null && selectedCycle != null && !shellState.showExpandedPlayer) {
            shellViewModel.closeCycle()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            when {
                selectedBook != null -> {
                    val bookDetailViewModel: BookDetailViewModel = hiltViewModel(key = selectedBook.id)
                    val detailState by bookDetailViewModel.uiState.collectAsState()

                    LaunchedEffect(selectedBook.id) {
                        bookDetailViewModel.loadBook(selectedBook)
                    }

                    BookDetailScreen(
                        padding = padding,
                        book = selectedBook,
                        uiState = detailState,
                        onBack = shellViewModel::closeBook,
                        onTrackClick = bookDetailViewModel::playTrack,
                        onConfirmDownload = bookDetailViewModel::downloadBook,
                        onDismissDownloadSheet = bookDetailViewModel::dismissDownloadSheet,
                        onShowTrackActions = bookDetailViewModel::showTrackActions,
                        onDismissTrackActions = bookDetailViewModel::dismissTrackActions,
                        onMarkComplete = bookDetailViewModel::markTrackComplete,
                        onPlayNext = bookDetailViewModel::playNextTrack,
                        onRemoveDownload = bookDetailViewModel::removeTrackDownload,
                    )
                }

                selectedCycle != null -> CycleDetailScreen(
                    padding = PaddingValues(0.dp),
                    cycle = selectedCycle,
                    downloadedBookIds = libraryState.downloadedBookIds,
                    onBack = shellViewModel::closeCycle,
                    onBookClick = shellViewModel::openBook,
                )

                shellState.currentTab == BottomDestination.Library -> LibraryScreen(
                    padding = padding,
                    cycles = libraryViewModel.filteredCycles,
                    allCycles = libraryState.cycles,
                    books = libraryViewModel.filteredBooks,
                    allBooks = libraryState.books,
                    downloadedBookIds = libraryState.downloadedBookIds,
                    favoriteBookIds = libraryState.favoriteBookIds,
                    offlineBanner = libraryState.sessionState == SessionState.AUTHENTICATED_OFFLINE,
                    isLoadingCatalog = libraryState.isLoadingCatalog,
                    filter = libraryState.filter,
                    showFilterSheet = libraryState.showFilterSheet,
                    onCycleClick = shellViewModel::openCycle,
                    onBookClick = shellViewModel::openBook,
                    onSearchChange = libraryViewModel::setSearchQuery,
                    onFilterClick = { libraryViewModel.setFilterSheetVisible(true) },
                    onDismissFilterSheet = { libraryViewModel.setFilterSheetVisible(false) },
                    onApplyFilter = libraryViewModel::applyFilter,
                    onResetFilter = libraryViewModel::resetFilter,
                    onContentFilterChange = libraryViewModel::setContentFilter,
                    onSortOrderChange = libraryViewModel::setSortOrder,
                    musicPreview = libraryState.musicPreview,
                    musicPlayback = libraryState.musicPlayback,
                    musicDownloadProgress = libraryState.musicDownloadProgress,
                    musicPlaybackErrorRes = libraryState.musicPlaybackErrorRes,
                    onMusicPlayPause = libraryViewModel::toggleMusicPlayback,
                    onMusicShuffle = libraryViewModel::shuffleMusicPreview,
                    onMusicTabSelected = libraryViewModel::onMusicTabSelected,
                )

                shellState.currentTab == BottomDestination.Downloads -> DownloadsScreen(
                    padding = padding,
                    viewModel = downloadsViewModel,
                )

                shellState.currentTab == BottomDestination.Profile -> ProfileScreen(
                    padding = padding,
                    viewModel = profileViewModel,
                    onOpenDownloads = { shellViewModel.selectTab(BottomDestination.Downloads) },
                )
            }

            if (shellState.showExpandedPlayer) {
                NowPlayingSheet(
                    shellState = shellState,
                    onDismiss = shellViewModel::dismissExpandedPlayer,
                )
            }
        }
    }
}
