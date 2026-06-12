package com.tonezen.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.playback.PlaybackClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NowPlayingUiState(
    val title: String? = null,
    val subtitle: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val activeBook: Book? = null,
    val upNext: List<Track> = emptyList(),
    val queueCount: Int = 0,
    val favoritesCount: Int = 0,
    val downloadsCount: Int = 0,
    val hasSyncedAudiobooks: Boolean = false,
)

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackClient: PlaybackClient,
    private val catalogRepository: CatalogRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NowPlayingUiState())
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
                _uiState.update {
                    it.copy(
                        title = snapshot.trackTitle,
                        isPlaying = snapshot.isPlaying,
                        positionMs = snapshot.positionMs,
                        durationMs = snapshot.durationMs,
                    )
                }
                snapshot.trackId?.let { trackId -> refreshQueue(trackId) }
            }
        }
        refreshCounts()
    }

    fun refreshCounts() {
        viewModelScope.launch {
            val books = catalogRepository.getAllBooks()
            val favorites = catalogRepository.getFavoriteBookIds()
            val downloaded = catalogRepository.downloadedBookIds(books)
            _uiState.update {
                it.copy(
                    queueCount = it.upNext.size,
                    favoritesCount = favorites.size,
                    downloadsCount = downloaded.size,
                    hasSyncedAudiobooks = books.any { book ->
                        book.contentType.name == "AUDIOBOOK" &&
                            !catalogRepository.isProgressPendingSync(book.id)
                    },
                )
            }
        }
    }

    fun pauseOrResume() {
        if (_uiState.value.isPlaying) playbackClient.pause() else playbackClient.play()
    }

    fun seekBy(deltaMs: Long) {
        playbackClient.seekBy(deltaMs)
    }

    fun skipPrevious() {
        playbackClient.skipToPrevious()
    }

    fun skipNext() {
        playbackClient.skipToNext()
    }

    private suspend fun refreshQueue(activeTrackId: String) {
        val books = catalogRepository.getAllBooks()
        for (book in books) {
            val tracks = catalogRepository.getTracksForBook(book.id)
            val downloaded = tracks.filter { it.localPath != null }.sortedBy { it.sortOrder }
            val index = downloaded.indexOfFirst { it.id == activeTrackId }
            if (index >= 0) {
                _uiState.update {
                    it.copy(
                        activeBook = book,
                        subtitle = book.author,
                        upNext = downloaded.drop(index + 1),
                        queueCount = downloaded.size,
                    )
                }
                return
            }
        }
    }
}
