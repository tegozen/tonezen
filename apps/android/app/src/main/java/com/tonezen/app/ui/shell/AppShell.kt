package com.tonezen.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.domain.library.filterAndSortBooks
import com.tonezen.app.domain.library.filterCycles
import com.tonezen.app.ui.components.BottomDestination
import com.tonezen.app.ui.components.MiniPlayer
import com.tonezen.app.ui.components.TonezenBottomChromeBar
import com.tonezen.app.ui.components.TonezenBottomNavigation
import com.tonezen.app.ui.components.TonezenTitleChromeBar
import com.tonezen.app.ui.downloads.DownloadsTabScreen
import com.tonezen.app.ui.library.CycleCardState
import com.tonezen.app.ui.library.CycleDetailScreen
import com.tonezen.app.ui.library.LibraryScreen
import com.tonezen.app.ui.library.LibrarySection
import com.tonezen.app.ui.library.LibraryViewModel
import com.tonezen.app.ui.library.visibleMusicTrackList
import com.tonezen.app.ui.player.BookDetailScreen
import com.tonezen.app.ui.player.BookDetailViewModel
import com.tonezen.app.ui.player.NowPlayingSheet
import com.tonezen.app.ui.profile.ProfileScreen
import com.tonezen.app.ui.profile.AvatarCropScreen
import com.tonezen.app.ui.profile.ProfileViewModel
import com.tonezen.app.ui.profile.resolveAvatarUploadError
import com.tonezen.app.playback.forMusic
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenPageChromeScrollPadding
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.withoutBottom
import com.tonezen.app.ui.theme.tonezenBottomChromeScrollPadding
import dev.chrisbanes.haze.HazeState

@Composable
fun AppShell(
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    shellViewModel: AppShellViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
) {
    val libraryState by libraryViewModel.uiState.collectAsState()
    val shellState by shellViewModel.uiState.collectAsState()
    val profileState by profileViewModel.uiState.collectAsState()
    val downloadQueue by shellViewModel.downloadQueueState.collectAsState()
    val musicDownload by shellViewModel.musicDownloadState.collectAsState()
    val selectedBook = shellState.selectedBook
    val selectedCycle = shellState.selectedCycle
    val inLibraryOverlay = selectedCycle != null || selectedBook != null
    val isAvatarCropping = profileState.avatarCropUri != null
    val miniPlayerVisible = shellState.showMiniPlayer && !shellState.nowPlayingTitle.isNullOrBlank()
    val showBottomChrome = (miniPlayerVisible || !inLibraryOverlay) && !isAvatarCropping
    val overlayBottomScrollPadding = tonezenBottomChromeScrollPadding(
        showMiniPlayer = miniPlayerVisible,
        showBottomNav = false,
    )
    val hazeState = remember { HazeState() }
    val filteredCycles by remember {
        derivedStateOf {
            filterCycles(
                cycles = libraryState.cycles,
                downloadedBookIds = libraryState.downloadedBookIds,
                filter = libraryState.filter,
                progressUpdatedAtByBookId = libraryState.progressUpdatedAtByBookId,
            )
        }
    }
    val filteredBooks by remember {
        derivedStateOf {
            filterAndSortBooks(
                books = libraryState.books,
                downloadedBookIds = libraryState.downloadedBookIds,
                filter = libraryState.filter,
                progressUpdatedAtByBookId = libraryState.progressUpdatedAtByBookId,
            )
        }
    }
    val visibleMusicTracks by remember {
        derivedStateOf {
            visibleMusicTrackList(libraryState.musicTrackList, libraryState.isNetworkOnline)
        }
    }

    LaunchedEffect(shellState.currentTab) {
        if (shellState.currentTab == BottomDestination.Music) {
            libraryViewModel.onMusicTabSelected()
        }
        if (shellState.currentTab != BottomDestination.Books) {
            libraryViewModel.setFilterSheetVisible(false)
        }
    }

    Scaffold(
        containerColor = TonezenSurface,
    ) { padding ->
        val contentPadding = padding.withoutBottom()

        BackHandler(enabled = shellState.showExpandedPlayer) {
            shellViewModel.dismissExpandedPlayer()
        }
        BackHandler(enabled = isAvatarCropping) {
            profileViewModel.dismissAvatarCrop()
        }
        BackHandler(enabled = selectedBook != null && !shellState.showExpandedPlayer) {
            shellViewModel.closeBook()
        }
        BackHandler(enabled = selectedBook == null && selectedCycle != null && !shellState.showExpandedPlayer) {
            shellViewModel.closeCycle()
        }

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                when {
                    selectedBook != null -> {
                        val bookDetailViewModel: BookDetailViewModel = hiltViewModel(key = selectedBook.id)
                        val detailState by bookDetailViewModel.uiState.collectAsState()

                        LaunchedEffect(selectedBook.id) {
                            bookDetailViewModel.loadBook(selectedBook)
                        }

                    BookDetailScreen(
                        padding = PaddingValues(0.dp),
                        hazeState = hazeState,
                        book = selectedBook,
                            uiState = detailState,
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
                            bottomScrollPadding = overlayBottomScrollPadding,
                        )
                    }

                    selectedCycle != null -> {
                        LaunchedEffect(selectedCycle.id) {
                            libraryViewModel.refreshCycleMenu(selectedCycle)
                        }
                        CycleDetailScreen(
                            padding = PaddingValues(0.dp),
                            hazeState = hazeState,
                            cycle = selectedCycle,
                            cycleCardState = libraryState.cycleCardStateById[selectedCycle.id]
                                ?: CycleCardState(),
                            downloadedBookIds = libraryState.downloadedBookIds,
                            tracksByBookId = libraryState.tracksByBookId,
                            progressByBookId = libraryState.audiobookProgressByBookId,
                            onBack = shellViewModel::closeCycle,
                            onBookClick = shellViewModel::openBook,
                            onDownloadCycle = { libraryViewModel.downloadCycle(selectedCycle) },
                            onToggleCycleListened = { libraryViewModel.toggleCycleListened(selectedCycle) },
                            onRemoveCycleDownloads = { libraryViewModel.removeCycleDownloads(selectedCycle) },
                            bottomScrollPadding = overlayBottomScrollPadding,
                        )
                    }

                    shellState.currentTab == BottomDestination.Music ||
                        shellState.currentTab == BottomDestination.Books -> {
                        val section = if (shellState.currentTab == BottomDestination.Music) {
                            LibrarySection.Music
                        } else {
                            LibrarySection.Books
                        }
                        LibraryScreen(
                            hazeState = hazeState,
                            section = section,
                            cycles = filteredCycles,
                            allCycles = libraryState.cycles,
                            books = filteredBooks,
                            allBooks = libraryState.books,
                            downloadedBookIds = libraryState.downloadedBookIds,
                            cycleCardStateById = libraryState.cycleCardStateById,
                            cyclePlayback = libraryState.cyclePlayback,
                            offlineBanner = libraryState.sessionState == SessionState.AUTHENTICATED_OFFLINE,
                            isLoadingCatalog = libraryState.isLoadingCatalog,
                            filter = libraryState.filter,
                            showFilterSheet = libraryState.showFilterSheet,
                            onCycleClick = shellViewModel::openCycle,
                            onCyclePlay = libraryViewModel::toggleCyclePlay,
                            onBookClick = shellViewModel::openBook,
                            onSearchChange = libraryViewModel::setSearchQuery,
                            onFilterClick = { libraryViewModel.setFilterSheetVisible(true) },
                            onDismissFilterSheet = { libraryViewModel.setFilterSheetVisible(false) },
                            onApplyFilter = libraryViewModel::applyFilter,
                            onResetFilter = libraryViewModel::resetFilter,
                            onContentFilterChange = libraryViewModel::setContentFilter,
                            onSortOrderChange = libraryViewModel::setSortOrder,
                            musicTrackList = visibleMusicTracks,
                            musicPlayback = libraryState.musicPlayback,
                            downloadQueue = downloadQueue,
                            musicPlaybackErrorMessage = libraryState.musicPlaybackErrorMessage,
                            cyclePlaybackErrorMessage = libraryState.cyclePlaybackErrorMessage,
                            onMusicWavePlay = libraryViewModel::playMusicWave,
                            onMusicTrackClick = libraryViewModel::onMusicTrackClick,
                            onDownloadMusicTrack = libraryViewModel::downloadMusicTrack,
                            onDeleteMusicTrack = libraryViewModel::deleteMusicTrack,
                            onDownloadAllMusic = libraryViewModel::downloadAllMusic,
                            onMusicTabSelected = libraryViewModel::onMusicTabSelected,
                            showMiniPlayer = shellState.showMiniPlayer,
                            isNetworkOnline = libraryState.isNetworkOnline,
                        )
                    }

                    shellState.currentTab == BottomDestination.Downloads -> Box(modifier = Modifier.fillMaxSize()) {
                        DownloadsTabScreen(
                            hazeState = hazeState,
                            topPadding = TonezenPageChromeScrollPadding,
                            bottomPadding = tonezenBottomChromeScrollPadding(
                                showMiniPlayer = miniPlayerVisible,
                                showBottomNav = true,
                            ),
                            offlineBanner = libraryState.sessionState == SessionState.AUTHENTICATED_OFFLINE,
                        )
                        TonezenTitleChromeBar(
                            modifier = Modifier.align(Alignment.TopCenter),
                            hazeState = hazeState,
                        ) {
                            Text(
                                text = "Загрузки",
                                color = TonezenInk,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }

                    else -> ProfileScreen(
                        padding = PaddingValues(0.dp),
                        hazeState = hazeState,
                        viewModel = profileViewModel,
                        showMiniPlayer = shellState.showMiniPlayer,
                    )
                }
            }

            if (showBottomChrome) {
                TonezenBottomChromeBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    hazeState = hazeState,
                    showMiniPlayerSlot = miniPlayerVisible,
                ) {
                    if (miniPlayerVisible) {
                        MiniPlayer(
                            title = shellState.nowPlayingTitle,
                            subtitle = shellState.nowPlayingSubtitle,
                            coverSeed = shellState.nowPlayingCoverSeed,
                            enabled = true,
                            isPlaying = shellState.isPlaying,
                            positionMs = shellState.positionMs,
                            durationMs = shellState.durationMs,
                            downloadProgress = if (libraryState.musicPlayback.isActive) {
                                shellState.nowPlayingCoverSeed
                                    ?.let { downloadQueue.forMusic().progressForTrack(it) }
                            } else {
                                null
                            },
                            onBarClick = shellViewModel::onMiniPlayerClick,
                            onPlayPauseClick = {
                                if (libraryState.musicPlayback.isActive) {
                                    libraryViewModel.onMiniPlayerPlayPause()
                                } else {
                                    shellViewModel.onMiniPlayerPlayPause()
                                }
                            },
                        )
                    }
                    if (!inLibraryOverlay) {
                        TonezenBottomNavigation(
                            selected = shellState.currentTab,
                            onSelect = shellViewModel::selectTab,
                        )
                    }
                }
            }

            NowPlayingSheet(
                visible = shellState.showExpandedPlayer,
                hazeState = hazeState,
                shellState = shellState,
                musicDownload = musicDownload,
                onDismiss = shellViewModel::dismissExpandedPlayer,
            )

            if (isAvatarCropping) {
                AvatarCropScreen(
                    imageUri = checkNotNull(profileState.avatarCropUri),
                    uploading = profileState.avatarUploading,
                    uploadError = resolveAvatarUploadError(profileState.avatarUploadError),
                    onBack = profileViewModel::dismissAvatarCrop,
                    onConfirm = profileViewModel::uploadAvatar,
                )
            }
        }
    }
}
