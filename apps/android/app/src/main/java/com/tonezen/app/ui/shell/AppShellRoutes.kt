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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.ui.bookwatch.BookWatchSettingsOverlay
import com.tonezen.app.ui.bookwatch.BookWatchViewModel
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
import com.tonezen.app.ui.navigation.BookRoute
import com.tonezen.app.ui.navigation.BooksRoute
import com.tonezen.app.ui.navigation.CycleRoute
import com.tonezen.app.ui.navigation.DownloadsRoute
import com.tonezen.app.ui.navigation.MusicRoute
import com.tonezen.app.ui.profile.ProfileViewModel
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenPageChromeScrollPadding
import com.tonezen.app.ui.theme.tonezenBottomChromeScrollPadding
import dev.chrisbanes.haze.HazeState

@Composable
internal fun AppShellRoutes(
    navController: NavHostController,
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
    var editingWatchCycle by remember { mutableStateOf<Cycle?>(null) }
    val miniPlayerVisible = shellState.showMiniPlayer && !shellState.nowPlayingTitle.isNullOrBlank()

    NavHost(navController = navController, startDestination = MusicRoute) {
        composable<MusicRoute> {
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

        composable<BooksRoute> {
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
                onCycleClick = { cycle ->
                    navController.navigate(CycleRoute(cycle.id)) { launchSingleTop = true }
                },
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

        composable<CycleRoute> { entry ->
            val route = entry.toRoute<CycleRoute>()
            val cycle = libraryState.cycles.firstOrNull { it.id == route.cycleId }?.let { current ->
                current.copy(title = watches.firstOrNull { it.cycleId == current.id }?.displayTitle ?: current.title)
            }
            MissingCatalogDestinationEffect(cycle, libraryState.isLoadingCatalog, navController)
            if (cycle != null) {
                LaunchedEffect(cycle.id) { libraryViewModel.refreshCycleMenu(cycle) }
                CycleDetailScreen(
                    padding = PaddingValues(0.dp),
                    hazeState = hazeState,
                    cycle = cycle,
                    cycleCardState = libraryState.cycleCardStateById[cycle.id] ?: CycleCardState(),
                    downloadedBookIds = libraryState.downloadedBookIds,
                    tracksByBookId = libraryState.tracksByBookId,
                    progressByBookId = libraryState.audiobookProgressByBookId,
                    onBack = { navController.popBackStack() },
                    onBookClick = { book ->
                        navController.navigate(BookRoute(book.id)) { launchSingleTop = true }
                    },
                    onDownloadCycle = { libraryViewModel.downloadCycle(cycle) },
                    onToggleCycleListened = { libraryViewModel.toggleCycleListened(cycle) },
                    onRemoveCycleDownloads = { libraryViewModel.removeCycleDownloads(cycle) },
                    bottomScrollPadding = overlayBottomScrollPadding,
                    onBookWatch = { editingWatchCycle = cycle },
                )
            }
        }

        composable<BookRoute> { entry ->
            val route = entry.toRoute<BookRoute>()
            val book = libraryState.cycles.asSequence()
                .flatMap { it.books.asSequence() }
                .firstOrNull { it.id == route.bookId }
            MissingCatalogDestinationEffect(book, libraryState.isLoadingCatalog, navController)
            if (book != null) {
                AppShellBookDetailRoute(
                    book = book,
                    hazeState = hazeState,
                    overlayBottomScrollPadding = overlayBottomScrollPadding,
                    autoResume = route.autoResume,
                    onBack = { navController.popBackStack() },
                )
            }
        }

        composable<DownloadsRoute> {
            Box(modifier = Modifier.fillMaxSize()) {
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
        }

        profileRoutes(
            navController = navController,
            profileViewModel = profileViewModel,
            bookWatchViewModel = bookWatchViewModel,
            hazeState = hazeState,
            showMiniPlayer = shellState.showMiniPlayer,
        )
    }

    editingWatchCycle?.let { cycle ->
        BookWatchSettingsOverlay(
            cycleId = cycle.id,
            cycleTitle = cycle.title,
            viewModel = bookWatchViewModel,
            onDismiss = { editingWatchCycle = null },
        )
    }
}

@Composable
private fun MissingCatalogDestinationEffect(
    item: Any?,
    isLoadingCatalog: Boolean,
    navController: NavHostController,
) {
    LaunchedEffect(item, isLoadingCatalog) {
        if (item == null && !isLoadingCatalog) {
            val returnedToBooks = navController.popBackStack<BooksRoute>(inclusive = false)
            if (!returnedToBooks) {
                navController.navigate(BooksRoute) { launchSingleTop = true }
            }
        }
    }
}
