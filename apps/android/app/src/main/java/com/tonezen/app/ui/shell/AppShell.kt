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
import com.tonezen.app.domain.library.filterCycles
import com.tonezen.app.ui.components.BottomDestination
import com.tonezen.app.ui.components.MiniPlayer
import com.tonezen.app.ui.components.TonezenBottomChromeBar
import com.tonezen.app.ui.components.TonezenBottomNavigation
import com.tonezen.app.ui.library.LibraryViewModel
import com.tonezen.app.ui.music.MusicViewModel
import com.tonezen.app.ui.music.visibleMusicTrackList
import com.tonezen.app.ui.player.NowPlayingSheet
import com.tonezen.app.ui.profile.AvatarCropScreen
import com.tonezen.app.ui.profile.ProfileViewModel
import com.tonezen.app.ui.bookwatch.BookWatchViewModel
import com.tonezen.app.ui.bookwatch.BookWatchNavigation
import com.tonezen.app.ui.profile.ProfileSettingsAction
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

    LaunchedEffect(shellState.currentTab) {
        if (shellState.currentTab == BottomDestination.Music) {
            musicViewModel.onMusicTabSelected()
        }
        if (shellState.currentTab != BottomDestination.Books) {
            libraryViewModel.setFilterSheetVisible(false)
        }
    }
    LaunchedEffect(Unit) { bookWatchViewModel.checkOnLaunch() }
    LaunchedEffect(openBookWatch) {
        if (openBookWatch) {
            shellViewModel.selectTab(BottomDestination.Profile)
            profileViewModel.openSettingsScreen(ProfileSettingsAction.BookWatch)
            BookWatchNavigation.openRequested.value = false
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
    }
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
