package com.tonezen.app.ui.shell

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
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
import com.tonezen.app.ui.profile.resolveAvatarUploadError
import com.tonezen.app.ui.profile.uploadAvatar
import com.tonezen.app.playback.forMusic
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
) {
    val libraryState by libraryViewModel.uiState.collectAsState()
    val musicState by musicViewModel.uiState.collectAsState()
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
                    hazeState = hazeState,
                    shellState = shellState,
                    libraryState = libraryState,
                    musicState = musicState,
                    filteredCycles = filteredCycles,
                    visibleMusicTracks = visibleMusicTracks,
                    downloadQueue = downloadQueue,
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
                        MiniPlayer(
                            title = shellState.nowPlayingTitle,
                            subtitle = shellState.nowPlayingSubtitle,
                            coverSeed = shellState.nowPlayingCoverSeed,
                            enabled = true,
                            isPlaying = shellState.isPlaying,
                            positionMs = shellState.positionMs,
                            durationMs = shellState.durationMs,
                            downloadProgress = if (musicState.musicPlayback.isActive) {
                                shellState.nowPlayingCoverSeed
                                    ?.let { downloadQueue.forMusic().progressForTrack(it) }
                            } else {
                                null
                            },
                            onBarClick = shellViewModel::onMiniPlayerClick,
                            onPlayPauseClick = {
                                if (musicState.musicPlayback.isActive) {
                                    musicViewModel.onMiniPlayerPlayPause()
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
