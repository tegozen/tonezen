package com.tonezen.app.ui.shell

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.ui.components.BottomDestination
import com.tonezen.app.ui.components.TonezenTitleChromeBar
import com.tonezen.app.ui.downloads.DownloadsTabScreen
import com.tonezen.app.ui.library.CycleCardState
import com.tonezen.app.ui.library.CycleDetailScreen
import com.tonezen.app.ui.library.LibraryScreen
import com.tonezen.app.ui.library.LibraryUiState
import com.tonezen.app.ui.library.LibraryViewModel
import com.tonezen.app.ui.music.MusicListTrack
import com.tonezen.app.ui.music.MusicScreen
import com.tonezen.app.ui.music.MusicUiState
import com.tonezen.app.ui.music.MusicViewModel
import com.tonezen.app.ui.profile.ProfileScreen
import com.tonezen.app.ui.profile.ProfileViewModel
import com.tonezen.app.ui.bookwatch.BookWatchViewModel
import com.tonezen.app.ui.bookwatch.BookWatchSettingsDialog
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenPageChromeScrollPadding
import com.tonezen.app.ui.theme.tonezenBottomChromeScrollPadding
import dev.chrisbanes.haze.HazeState

@Composable
internal fun AppShellRoutes(
    libraryViewModel: LibraryViewModel,
    musicViewModel: MusicViewModel,
    shellViewModel: AppShellViewModel,
    profileViewModel: ProfileViewModel,
    bookWatchViewModel: BookWatchViewModel,
    hazeState: HazeState,
    shellState: AppShellUiState,
    libraryState: LibraryUiState,
    musicState: MusicUiState,
    filteredCycles: List<Cycle>,
    visibleMusicTracks: List<MusicListTrack>,
    overlayBottomScrollPadding: Dp,
) {
    val watches by bookWatchViewModel.watches.collectAsStateWithLifecycle()
    var editingWatch by remember { mutableStateOf<com.tonezen.app.data.local.BookWatchEntity?>(null) }
    val selectedBook = shellState.selectedBook
    val selectedCycle = shellState.selectedCycle?.let { cycle ->
        cycle.copy(title = watches.firstOrNull { it.cycleId == cycle.id }?.displayTitle ?: cycle.title)
    }
    val miniPlayerVisible = shellState.showMiniPlayer && !shellState.nowPlayingTitle.isNullOrBlank()
    val routeStateHolder = rememberSaveableStateHolder()
    val routeKey = when {
        selectedBook != null -> "book:${selectedBook.id}"
        selectedCycle != null -> "cycle:${selectedCycle.id}"
        else -> "tab:${shellState.currentTab.name}"
    }

    routeStateHolder.SaveableStateProvider(routeKey) {
        when {
            selectedBook != null -> AppShellBookDetailRoute(
                book = selectedBook,
                shellState = shellState,
                shellViewModel = shellViewModel,
                hazeState = hazeState,
                overlayBottomScrollPadding = overlayBottomScrollPadding,
            )

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
                    onBookResume = shellViewModel::resumeBook,
                    onDownloadCycle = { libraryViewModel.downloadCycle(selectedCycle) },
                    onToggleCycleListened = { libraryViewModel.toggleCycleListened(selectedCycle) },
                    onRemoveCycleDownloads = { libraryViewModel.removeCycleDownloads(selectedCycle) },
                    bottomScrollPadding = overlayBottomScrollPadding,
                    onBookWatch = { editingWatch = watches.firstOrNull { it.cycleId == selectedCycle.id } },
                )
            }

            shellState.currentTab == BottomDestination.Music -> {
                val musicDownload by shellViewModel.musicDownloadState.collectAsStateWithLifecycle()
                MusicScreen(
                    hazeState = hazeState,
                    hasMusicBooks = musicState.hasMusicBooks,
                    isLoadingCatalog = musicState.isLoadingCatalog,
                    musicTrackList = visibleMusicTracks,
                    musicPlayback = musicState.musicPlayback,
                    musicDownload = musicDownload,
                    musicPlaybackErrorMessage = musicState.musicPlaybackErrorMessage,
                    onDismissMusicPlaybackError = musicViewModel::clearMusicPlaybackError,
                    onMusicWavePlay = musicViewModel::playMusicWave,
                    onMusicTrackClick = musicViewModel::onMusicTrackClick,
                    onDownloadMusicTrack = musicViewModel::downloadMusicTrack,
                    onDeleteMusicTrack = musicViewModel::deleteMusicTrack,
                    onDownloadAllMusic = musicViewModel::downloadAllMusic,
                    offlineBanner = libraryState.sessionState == SessionState.AUTHENTICATED_OFFLINE,
                    showMiniPlayer = shellState.showMiniPlayer,
                    isNetworkOnline = musicState.isNetworkOnline,
                )
            }

            shellState.currentTab == BottomDestination.Books -> {
                LibraryScreen(
                    hazeState = hazeState,
                    cycles = filteredCycles,
                    allCycles = libraryState.cycles.map { cycle ->
                        cycle.copy(title = watches.firstOrNull { it.cycleId == cycle.id }?.displayTitle ?: cycle.title)
                    },
                    cycleCardStateById = libraryState.cycleCardStateById,
                    cyclePlayback = libraryState.cyclePlayback,
                    offlineBanner = libraryState.sessionState == SessionState.AUTHENTICATED_OFFLINE,
                    isLoadingCatalog = libraryState.isLoadingCatalog,
                    filter = libraryState.filter,
                    showFilterSheet = libraryState.showFilterSheet,
                    onCycleClick = shellViewModel::openCycle,
                    onCyclePlay = libraryViewModel::toggleCyclePlay,
                    onSearchChange = libraryViewModel::setSearchQuery,
                    onFilterClick = { libraryViewModel.setFilterSheetVisible(true) },
                    onDismissFilterSheet = { libraryViewModel.setFilterSheetVisible(false) },
                    onApplyFilter = libraryViewModel::applyFilter,
                    onResetFilter = libraryViewModel::resetFilter,
                    onContentFilterChange = libraryViewModel::setContentFilter,
                    onSortOrderChange = libraryViewModel::setSortOrder,
                    cyclePlaybackErrorMessage = libraryState.cyclePlaybackErrorMessage,
                    onDismissCyclePlaybackError = libraryViewModel::clearCyclePlaybackError,
                    confirmProgressSyncConflict = libraryState.confirmProgressSyncConflict,
                    onDismissProgressSyncConflict = libraryViewModel::dismissCycleProgressSyncConflict,
                    onChooseProgressSyncLocal = libraryViewModel::chooseCycleProgressSyncLocal,
                    onChooseProgressSyncServer = libraryViewModel::chooseCycleProgressSyncServer,
                    showMiniPlayer = shellState.showMiniPlayer,
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
                bookWatchViewModel = bookWatchViewModel,
                showMiniPlayer = shellState.showMiniPlayer,
            )
        }
    }
    editingWatch?.let { watch ->
        BookWatchSettingsDialog(
            watch = watch,
            onDismiss = { editingWatch = null },
            onSave = { title, queries -> bookWatchViewModel.update(watch, title, queries); editingWatch = null },
        )
    }
}
