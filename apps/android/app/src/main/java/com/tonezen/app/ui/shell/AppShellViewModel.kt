package com.tonezen.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.ui.components.BottomDestination
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AppShellViewModel @Inject constructor(
    private val playbackClient: PlaybackClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AppShellUiState())
    val uiState: StateFlow<AppShellUiState> = _uiState.asStateFlow()

    init {
        playbackClient.connect()
        viewModelScope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
                _uiState.update {
                    it.copy(
                        isPlaying = snapshot.isPlaying,
                        nowPlayingTitle = snapshot.trackTitle ?: it.nowPlayingTitle,
                        nowPlayingSubtitle = formatNowPlayingSubtitle(snapshot.artist, snapshot.albumTitle)
                            ?: it.nowPlayingSubtitle,
                        nowPlayingCoverSeed = snapshot.trackId ?: snapshot.trackTitle,
                        positionMs = snapshot.positionMs,
                        durationMs = snapshot.durationMs,
                        showMiniPlayer = snapshot.trackTitle != null || snapshot.isPlaying,
                    )
                }
            }
        }
    }

    fun selectTab(tab: BottomDestination) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun openCycle(cycle: Cycle) {
        _uiState.update { it.copy(selectedCycle = cycle, selectedBook = null) }
    }

    fun closeCycle() {
        _uiState.update { it.copy(selectedCycle = null) }
    }

    fun openBook(book: Book) {
        _uiState.update {
            it.copy(
                selectedBook = book,
                showExpandedPlayer = false,
                nowPlayingSubtitle = book.author,
            )
        }
    }

    fun closeBook() {
        _uiState.update { it.copy(selectedBook = null) }
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

    fun updateNowPlaying(title: String?, subtitle: String?) {
        _uiState.update {
            it.copy(
                nowPlayingTitle = title ?: it.nowPlayingTitle,
                nowPlayingSubtitle = subtitle ?: it.nowPlayingSubtitle,
                showMiniPlayer = title != null,
            )
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
