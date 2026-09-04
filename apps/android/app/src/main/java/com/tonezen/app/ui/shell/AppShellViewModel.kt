package com.tonezen.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.MusicDownloadState
import com.tonezen.app.playback.toMusicDownloadState
import com.tonezen.app.playback.PlaybackClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val playbackClient: PlaybackClient,
    downloadQueueNotifier: DownloadQueueNotifier,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppShellUiState())
    val uiState: StateFlow<AppShellUiState> = _uiState.asStateFlow()

    val playbackProgress: StateFlow<PlaybackProgress> = playbackClient.snapshot
        .map { snapshot ->
            PlaybackProgress(
                positionMs = snapshot.positionMs.coerceAtLeast(0L),
                durationMs = snapshot.durationMs.coerceAtLeast(0L),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlaybackProgress())

    val musicDownloadState: StateFlow<MusicDownloadState> = downloadQueueNotifier.state
        .map { it.toMusicDownloadState() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MusicDownloadState())

    init {
        playbackClient.connect()
        viewModelScope.launch {
            playbackClient.snapshot
                .map { snapshot ->
                    val trackId = snapshot.trackId?.takeIf { it.isNotBlank() }
                    val trackTitle = snapshot.trackTitle?.takeIf { it.isNotBlank() }
                    val hasPlayback = trackId != null || trackTitle != null
                    if (!hasPlayback) {
                        ShellPlaybackChrome(
                            isPlaying = false,
                            nowPlayingTitle = null,
                            nowPlayingSubtitle = null,
                            nowPlayingCoverSeed = null,
                            showMiniPlayer = false,
                        )
                    } else {
                        ShellPlaybackChrome(
                            isPlaying = snapshot.isPlaying,
                            nowPlayingTitle = trackTitle,
                            nowPlayingSubtitle = formatNowPlayingSubtitle(snapshot.artist, snapshot.albumTitle),
                            nowPlayingCoverSeed = trackId ?: trackTitle,
                            showMiniPlayer = true,
                        )
                    }
                }
                .distinctUntilChanged()
                .collect { chrome ->
                    _uiState.update { state ->
                        val next = state.copy(
                            isPlaying = chrome.isPlaying,
                            nowPlayingTitle = chrome.nowPlayingTitle,
                            nowPlayingSubtitle = chrome.nowPlayingSubtitle,
                            nowPlayingCoverSeed = chrome.nowPlayingCoverSeed,
                            showMiniPlayer = chrome.showMiniPlayer,
                            showExpandedPlayer = if (chrome.showMiniPlayer) {
                                state.showExpandedPlayer
                            } else {
                                false
                            },
                        )
                        if (next == state) state else next
                    }
                }
        }
    }

    fun onMiniPlayerClick() {
        _uiState.update { it.copy(showExpandedPlayer = true) }
    }

    fun dismissExpandedPlayer() {
        _uiState.update { it.copy(showExpandedPlayer = false) }
    }

    fun onMiniPlayerPlayPause() {
        if (_uiState.value.isPlaying) {
            playbackClient.pause()
        } else {
            playbackClient.play()
        }
    }

    private fun formatNowPlayingSubtitle(artist: String?, album: String?): String? {
        val cleanArtist = artist?.takeIf { it.isNotBlank() }
        val cleanAlbum = album?.takeIf { it.isNotBlank() }
        return when {
            cleanArtist != null && cleanAlbum != null -> "$cleanArtist · $cleanAlbum"
            cleanArtist != null -> cleanArtist
            cleanAlbum != null -> cleanAlbum
            else -> null
        }
    }
}

private data class ShellPlaybackChrome(
    val isPlaying: Boolean,
    val nowPlayingTitle: String?,
    val nowPlayingSubtitle: String?,
    val nowPlayingCoverSeed: String?,
    val showMiniPlayer: Boolean,
)
