package com.tonezen.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.tonezen.app.domain.library.filterCycles
import com.tonezen.app.ui.components.BottomDestination
import com.tonezen.app.ui.components.MiniPlayer
import com.tonezen.app.ui.components.TonezenBottomChromeBar
import com.tonezen.app.ui.components.TonezenBottomNavigation
import com.tonezen.app.ui.library.LibraryViewModel
import com.tonezen.app.ui.music.MusicViewModel
import com.tonezen.app.ui.music.visibleMusicTrackList
import com.tonezen.app.ui.navigation.AccountSettingsRoute
import com.tonezen.app.ui.navigation.BookRoute
import com.tonezen.app.ui.navigation.BookWatchRoute
import com.tonezen.app.ui.navigation.BooksRoute
import com.tonezen.app.ui.navigation.CycleRoute
import com.tonezen.app.ui.navigation.DownloadsRoute
import com.tonezen.app.ui.navigation.MusicRoute
import com.tonezen.app.ui.navigation.ProfileRoute
import com.tonezen.app.ui.navigation.StorageSettingsRoute
import com.tonezen.app.ui.navigation.route
import com.tonezen.app.ui.player.NowPlayingSheet
import com.tonezen.app.ui.profile.AvatarCropScreen
import com.tonezen.app.ui.profile.ProfileViewModel
import com.tonezen.app.ui.bookwatch.BookWatchViewModel
import com.tonezen.app.ui.bookwatch.BookWatchNavigation
import com.tonezen.app.ui.profile.resolveAvatarUploadError
import com.tonezen.app.ui.profile.uploadAvatar
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.withoutBottom
import com.tonezen.app.ui.theme.tonezenBottomChromeScrollPadding
import dev.chrisbanes.haze.HazeState

@Composable
fun AppShell(
    libraryViewModel: LibraryViewModel = hiltViewModel(),
    musicViewModel: MusicViewModel = hiltViewModel(),
    shellViewModel: AppShellViewModel = hiltViewModel(),
    profileViewModel: ProfileViewModel = hiltViewModel(),
    bookWatchViewModel: BookWatchViewModel = hiltViewModel(),
) {
    val libraryState by libraryViewModel.uiState.collectAsStateWithLifecycle()
    val musicState by musicViewModel.uiState.collectAsStateWithLifecycle()
    val shellState by shellViewModel.uiState.collectAsStateWithLifecycle()
    val profileState by profileViewModel.uiState.collectAsStateWithLifecycle()
    val bookWatches by bookWatchViewModel.watches.collectAsStateWithLifecycle()
    val openBookWatch by BookWatchNavigation.openRequested.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val currentTab = currentDestination.bottomDestination()
    val inLibraryOverlay = currentDestination.hasRoute<BookRoute>() || currentDestination.hasRoute<CycleRoute>()
    val isAvatarCropping = profileState.avatarCropUri != null
    val miniPlayerVisible = shellState.showMiniPlayer && !shellState.nowPlayingTitle.isNullOrBlank()
    val showBottomChrome = (miniPlayerVisible || !inLibraryOverlay) && !isAvatarCropping
    val overlayBottomScrollPadding = tonezenBottomChromeScrollPadding(
        showMiniPlayer = miniPlayerVisible,
        showBottomNav = false,
    )
    val hazeState = remember { HazeState() }
    val filteredCycles by remember(libraryState, bookWatches) {
        derivedStateOf {
            val titles = bookWatches.associate { it.cycleId to it.displayTitle }
            filterCycles(
                cycles = libraryState.cycles.map { cycle -> cycle.copy(title = titles[cycle.id] ?: cycle.title) },
                downloadedBookIds = libraryState.downloadedBookIds,
                filter = libraryState.filter,
                progressUpdatedAtByBookId = libraryState.progressUpdatedAtByBookId,
            )
        }
    }
    val visibleMusicTracks by remember {
        derivedStateOf {
            visibleMusicTrackList(musicState.musicTrackList, musicState.isNetworkOnline)
        }
    }

    LaunchedEffect(currentTab) {
        if (currentTab == BottomDestination.Music) {
            musicViewModel.onMusicTabSelected()
        }
        if (currentTab != BottomDestination.Books) {
            libraryViewModel.setFilterSheetVisible(false)
        }
    }
    LaunchedEffect(Unit) { bookWatchViewModel.checkOnLaunch() }
    LaunchedEffect(openBookWatch) {
        if (openBookWatch) {
            navController.selectBottomDestination(BottomDestination.Profile)
            navController.navigate(BookWatchRoute) { launchSingleTop = true }
            BookWatchNavigation.openRequested.value = false
        }
    }

    Scaffold(
        containerColor = TonezenSurface,
    ) { padding ->
        val contentPadding = padding.withoutBottom()

        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
            ) {
                AppShellRoutes(
                    libraryViewModel = libraryViewModel,
                    musicViewModel = musicViewModel,
                    shellViewModel = shellViewModel,
                    profileViewModel = profileViewModel,
                    bookWatchViewModel = bookWatchViewModel,
                    hazeState = hazeState,
                    shellState = shellState,
                    libraryState = libraryState,
                    musicState = musicState,
                    filteredCycles = filteredCycles,
                    visibleMusicTracks = visibleMusicTracks,
                    overlayBottomScrollPadding = overlayBottomScrollPadding,
                    navController = navController,
                )
            }

            if (showBottomChrome) {
                TonezenBottomChromeBar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    hazeState = hazeState,
                    showMiniPlayerSlot = miniPlayerVisible,
                ) {
                    if (miniPlayerVisible) {
                        AppShellMiniPlayerSlot(
                            shellViewModel = shellViewModel,
                            musicViewModel = musicViewModel,
                            title = shellState.nowPlayingTitle,
                            subtitle = shellState.nowPlayingSubtitle,
                            coverSeed = shellState.nowPlayingCoverSeed,
                            isPlaying = shellState.isPlaying,
                            musicPlaybackActive = musicState.musicPlayback.isActive,
                        )
                    }
                    if (!inLibraryOverlay) {
                        TonezenBottomNavigation(
                            selected = currentTab,
                            onSelect = navController::selectBottomDestination,
                        )
                    }
                }
            }

            NowPlayingSheet(
                visible = shellState.showExpandedPlayer,
                hazeState = hazeState,
                shellState = shellState,
                musicDownloadState = shellViewModel.musicDownloadState,
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
        BackHandler(enabled = shellState.showExpandedPlayer) {
            shellViewModel.dismissExpandedPlayer()
        }
        BackHandler(enabled = isAvatarCropping) {
            profileViewModel.dismissAvatarCrop()
        }
    }
}

private fun NavHostController.selectBottomDestination(destination: BottomDestination) {
    navigate(destination.route()) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private inline fun <reified T : Any> NavDestination?.hasRoute(): Boolean =
    this?.hierarchy?.any { it.hasRoute<T>() } == true

private fun NavDestination?.bottomDestination(): BottomDestination = when {
    hasRoute<MusicRoute>() -> BottomDestination.Music
    hasRoute<BooksRoute>() || hasRoute<CycleRoute>() || hasRoute<BookRoute>() -> BottomDestination.Books
    hasRoute<DownloadsRoute>() -> BottomDestination.Downloads
    hasRoute<ProfileRoute>() || hasRoute<AccountSettingsRoute>() ||
        hasRoute<StorageSettingsRoute>() || hasRoute<BookWatchRoute>() -> BottomDestination.Profile
    else -> BottomDestination.Music
}

@Composable
private fun AppShellMiniPlayerSlot(
    shellViewModel: AppShellViewModel,
    musicViewModel: MusicViewModel,
    title: String?,
    subtitle: String?,
    coverSeed: String?,
    isPlaying: Boolean,
    musicPlaybackActive: Boolean,
) {
    val progress by shellViewModel.playbackProgress.collectAsStateWithLifecycle()
    val musicDownload by shellViewModel.musicDownloadState.collectAsStateWithLifecycle()
    MiniPlayer(
        title = title,
        subtitle = subtitle,
        coverSeed = coverSeed,
        enabled = true,
        isPlaying = isPlaying,
        positionMs = progress.positionMs,
        durationMs = progress.durationMs,
        downloadProgress = if (musicPlaybackActive) {
            coverSeed?.let { musicDownload.progressForTrack(it) }
        } else {
            null
        },
        onBarClick = shellViewModel::onMiniPlayerClick,
        onPlayPauseClick = {
            if (musicPlaybackActive) {
                musicViewModel.onMiniPlayerPlayPause()
            } else {
                shellViewModel.onMiniPlayerPlayPause()
            }
        },
    )
}
