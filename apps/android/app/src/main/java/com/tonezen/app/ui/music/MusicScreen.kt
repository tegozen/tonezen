package com.tonezen.app.ui.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tonezen.app.playback.MusicDownloadState
import com.tonezen.app.ui.components.EmptyLibrary
import com.tonezen.app.ui.components.LibraryLoading
import com.tonezen.app.ui.components.OfflineBanner
import com.tonezen.app.ui.components.TonezenTopChromeBar
import com.tonezen.app.ui.theme.TonezenChromeHeaderRowHeight
import com.tonezen.app.ui.theme.TonezenInk
import com.tonezen.app.ui.theme.TonezenPageChromeScrollPadding
import com.tonezen.app.ui.theme.TonezenSurface
import com.tonezen.app.ui.theme.TonezenTopChromeOfflineBannerExtra
import com.tonezen.app.ui.theme.tonezenBottomChromeScrollPadding
import com.tonezen.app.ui.theme.tonezenScreenContentPadding
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze

@Composable
internal fun MusicScreen(
    hazeState: HazeState,
    hasMusicBooks: Boolean,
    isLoadingCatalog: Boolean,
    musicTrackList: List<MusicListTrack>,
    musicPlayback: MusicPlaybackUi,
    musicDownload: MusicDownloadState,
    musicPlaybackErrorMessage: String?,
    onDismissMusicPlaybackError: () -> Unit,
    onMusicWavePlay: () -> Unit,
    onMusicTrackClick: (MusicListTrack) -> Unit,
    onDownloadMusicTrack: (MusicListTrack) -> Unit,
    onDeleteMusicTrack: (MusicListTrack) -> Unit,
    onDownloadAllMusic: () -> Unit,
    offlineBanner: Boolean,
    showMiniPlayer: Boolean,
    isNetworkOnline: Boolean = true,
) {
    var showAllMusicTracks by rememberSaveable { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(musicPlaybackErrorMessage) {
        musicPlaybackErrorMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            onDismissMusicPlaybackError()
        }
    }

    val topChromeScrollPadding = remember(offlineBanner) {
        if (offlineBanner) {
            TonezenPageChromeScrollPadding + TonezenTopChromeOfflineBannerExtra
        } else {
            TonezenPageChromeScrollPadding
        }
    }
    val bottomChromeScrollPadding = tonezenBottomChromeScrollPadding(
        showMiniPlayer = showMiniPlayer,
        showBottomNav = true,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .haze(state = hazeState)
                .background(TonezenSurface),
            contentPadding = tonezenScreenContentPadding(
                top = topChromeScrollPadding,
                bottom = bottomChromeScrollPadding,
            ),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (isLoadingCatalog) {
                item { LibraryLoading() }
            } else if (!hasMusicBooks) {
                item { EmptyLibrary(offline = offlineBanner) }
            } else if (musicTrackList.isEmpty()) {
                item { EmptyLibrary(offline = offlineBanner || !isNetworkOnline) }
            } else {
                item {
                    MusicWaveCard(
                        tracks = musicTrackList,
                        musicPlayback = musicPlayback,
                        isNetworkOnline = isNetworkOnline,
                        onClick = onMusicWavePlay,
                    )
                }
                item {
                    MusicDownloadAllButton(
                        tracks = musicTrackList,
                        musicDownload = musicDownload,
                        onClick = onDownloadAllMusic,
                    )
                }
                item {
                    MusicAllTracksToggle(
                        count = musicTrackList.size,
                        expanded = showAllMusicTracks,
                        onClick = { showAllMusicTracks = !showAllMusicTracks },
                    )
                }
                if (showAllMusicTracks) {
                    items(musicTrackList, key = { it.trackId }) { track ->
                        val isActive = musicPlayback.trackId == track.trackId
                        val trackDownloadProgress = musicDownload.progressForTrack(track.trackId)
                        val isDownloading = trackDownloadProgress != null
                        val isQueued = musicDownload.isTrackQueued(track.trackId)
                        MusicTrackRow(
                            track = track,
                            isActive = isActive,
                            isQueued = isQueued,
                            isDownloading = isDownloading,
                            downloadProgress = trackDownloadProgress,
                            onClick = { onMusicTrackClick(track) },
                            onDownloadClick = { onDownloadMusicTrack(track) },
                            onDeleteClick = { onDeleteMusicTrack(track) },
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = bottomChromeScrollPadding),
        )
        TonezenTopChromeBar(
            modifier = Modifier.align(Alignment.TopCenter),
            hazeState = hazeState,
        ) {
            if (offlineBanner) {
                OfflineBanner()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = TonezenChromeHeaderRowHeight),
                contentAlignment = Alignment.CenterStart,
            ) {
                Text(
                    text = "Музыка",
                    color = TonezenInk,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
