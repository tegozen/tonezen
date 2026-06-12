package com.tonezen.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackQueueBuilder
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
    val coverSeed: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val contentType: ContentType? = null,
    val activeBook: Book? = null,
    val upNext: List<Track> = emptyList(),
    val canSkip: Boolean = false,
)

@HiltViewModel
class NowPlayingViewModel @Inject constructor(
    private val playbackClient: PlaybackClient,
    private val catalogRepository: CatalogRepository,
    private val playbackQueueBuilder: PlaybackQueueBuilder,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NowPlayingUiState())
    val uiState: StateFlow<NowPlayingUiState> = _uiState.asStateFlow()

    init {
        playbackClient.connect()
        viewModelScope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
                _uiState.update {
                    it.copy(
                        title = snapshot.trackTitle,
                        subtitle = formatSubtitle(snapshot.artist, snapshot.albumTitle),
                        coverSeed = snapshot.trackId ?: snapshot.trackTitle,
                        isPlaying = snapshot.isPlaying,
                        positionMs = snapshot.positionMs,
                        durationMs = snapshot.durationMs,
                        contentType = snapshot.contentType,
                    )
                }
                snapshot.trackId?.let { trackId -> refreshUpNext(trackId) }
            }
        }
    }

    fun pauseOrResume() {
        if (_uiState.value.isPlaying) playbackClient.pause() else playbackClient.play()
    }

    fun seekTo(positionMs: Long) {
        playbackClient.seekTo(positionMs)
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

    fun playTrack(track: Track) {
        val book = _uiState.value.activeBook ?: return
        viewModelScope.launch {
            val item = when (book.contentType) {
                ContentType.MUSIC -> playbackQueueBuilder.buildSingleMusicItem(book, track)
                ContentType.AUDIOBOOK -> playbackQueueBuilder.buildSingleAudiobookItem(book, track)
            } ?: return@launch
            playbackClient.playQueue(listOf(item), startIndex = 0)
        }
    }

    private suspend fun refreshUpNext(activeTrackId: String) {
        val books = catalogRepository.getAllBooks()
        for (book in books) {
            val tracks = catalogRepository.getTracksForBook(book.id).sortedBy { it.sortOrder }
            val index = tracks.indexOfFirst { it.id == activeTrackId }
            if (index < 0) continue
            val upNext = tracks.drop(index + 1)
            _uiState.update {
                it.copy(
                    activeBook = book,
                    upNext = upNext,
                    canSkip = index > 0 || upNext.isNotEmpty(),
                )
            }
            return
        }
    }

    private fun formatSubtitle(artist: String?, album: String?): String? {
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
