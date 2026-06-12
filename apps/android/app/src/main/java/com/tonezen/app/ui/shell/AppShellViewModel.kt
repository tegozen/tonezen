package com.tonezen.app.ui.shell

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.domain.model.Book
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
        viewModelScope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
                _uiState.update {
                    it.copy(
                        isPlaying = snapshot.isPlaying,
                        nowPlayingTitle = snapshot.trackTitle ?: it.nowPlayingTitle,
                        showMiniPlayer = snapshot.trackTitle != null || snapshot.isPlaying,
                    )
                }
            }
        }
    }

    fun selectTab(tab: BottomDestination) {
        _uiState.update { it.copy(currentTab = tab) }
    }

    fun openBook(book: Book) {
        _uiState.update {
            it.copy(
                selectedBook = book,
                nowPlayingSubtitle = book.author,
            )
        }
    }

    fun closeBook() {
        _uiState.update { it.copy(selectedBook = null) }
    }

    fun onMiniPlayerClick() {
        _uiState.update { it.copy(currentTab = BottomDestination.Player) }
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
}
