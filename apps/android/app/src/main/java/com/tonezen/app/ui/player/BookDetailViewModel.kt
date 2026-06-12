package com.tonezen.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackEvents
import com.tonezen.app.playback.PlaybackMetadata
import com.tonezen.app.playback.QueuePlayItem
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val sessionRepository: SessionRepository,
    private val downloadRepository: DownloadRepository,
    private val progressSyncRepository: ProgressSyncRepository,
    private val playbackClient: PlaybackClient,
    private val playbackEvents: PlaybackEvents,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private var currentTrack: Track? = null
    private var lastProgressSaveMs = 0L

    init {
        viewModelScope.launch {
            progressSyncRepository.updates.collect { progress ->
                val book = _uiState.value.book ?: return@collect
                if (book.id != progress.bookId) return@collect
                val track = catalogRepository.getTracksForBook(book.id)
                    .find { it.id == progress.trackId }
                _uiState.update { it.copy(progressTrackTitle = track?.title) }
            }
        }
        viewModelScope.launch {
            playbackEvents.trackEnded.collect { onPlaybackEnded() }
        }
        viewModelScope.launch {
            playbackClient.activeTrackId.collect { trackId ->
                val track = _uiState.value.tracks.find { it.id == trackId } ?: return@collect
                currentTrack = track
                _uiState.update { it.copy(nowPlayingTitle = track.title) }
            }
        }
        viewModelScope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
                _uiState.update {
                    it.copy(
                        isPlaying = snapshot.isPlaying,
                        nowPlayingTitle = snapshot.trackTitle ?: it.nowPlayingTitle,
                    )
                }
                if (snapshot.isPlaying) {
                    maybeSaveProgress(snapshot.positionMs)
                }
            }
        }
    }

    fun loadBook(book: Book) {
        viewModelScope.launch {
            val tracks = catalogRepository.getTracksForBook(book.id)
            val progress = catalogRepository.getProgress(book.id)
            _uiState.update {
                it.copy(
                    book = book,
                    tracks = tracks,
                    progressTrackTitle = progress?.let { p ->
                        tracks.find { t -> t.id == p.trackId }?.title
                    },
                )
            }
        }
    }

    fun playBook() {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            val tracks = catalogRepository.getTracksForBook(book.id)
            val downloaded = tracks.filter { it.localPath != null }.sortedBy { it.sortOrder }
            if (downloaded.isEmpty()) return@launch
            val progress = catalogRepository.getProgress(book.id)
            val track = progress?.let { p -> downloaded.find { it.id == p.trackId } } ?: downloaded.first()
            val startMs = if (track.id == progress?.trackId) progress.positionMs else 0L
            startBookPlayback(book, downloaded, track, startMs)
        }
    }

    fun pausePlayback() {
        playbackClient.pause()
    }

    fun resumePlayback() {
        playbackClient.play()
    }

    fun downloadBook() {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            try {
                val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
                    ?: return@launch
                val accessToken = session.accessToken
                val tracks = catalogRepository.getTracksForBook(book.id)
                tracks.forEachIndexed { index, track ->
                    val file = downloadRepository.downloadTrack(
                        accessToken,
                        book.id,
                        track.id,
                    ) { progress ->
                        _uiState.update {
                            it.copy(downloadProgress = (index + progress) / tracks.size)
                        }
                    }
                    catalogRepository.markTrackDownloaded(book.id, track.id, file.absolutePath)
                }
                _uiState.update { it.copy(downloadProgress = null) }
                loadBook(book)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, downloadProgress = null) }
            }
        }
    }

    fun deleteLocalDownloads() {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            catalogRepository.clearLocalDownloads(book.id)
            val tracks = catalogRepository.getTracksForBook(book.id)
            tracks.forEach { track ->
                downloadRepository.deleteLocalTrack(book.id, track.id)
            }
            loadBook(book)
        }
    }

    fun toggleFavorite() {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            catalogRepository.toggleFavorite(book.id)
        }
    }

    private fun startBookPlayback(
        book: Book,
        downloadedTracks: List<Track>,
        startTrack: Track,
        startMs: Long,
    ) {
        val startIndex = downloadedTracks.indexOfFirst { it.id == startTrack.id }.coerceAtLeast(0)
        val queueItems = downloadedTracks.map { track ->
            QueuePlayItem(
                trackId = track.id,
                localPath = track.localPath!!,
                metadata = buildPlaybackMetadata(track, book, downloadedTracks),
            )
        }
        currentTrack = startTrack
        playbackClient.playQueue(queueItems, startIndex, startMs)
        _uiState.update {
            it.copy(nowPlayingTitle = startTrack.title, progressTrackTitle = null)
        }
    }

    private fun buildPlaybackMetadata(track: Track, book: Book, downloadedTracks: List<Track>): PlaybackMetadata {
        val trackNumber = (downloadedTracks.indexOfFirst { it.id == track.id } + 1).coerceAtLeast(1)
        return PlaybackMetadata(
            trackTitle = track.title,
            artist = book.author ?: book.title,
            albumTitle = book.title,
            trackNumber = trackNumber,
            totalTracks = downloadedTracks.size,
            contentType = book.contentType,
        )
    }

    private fun onPlaybackEnded() {
        val book = _uiState.value.book ?: return
        val track = currentTrack ?: return
        if (book.contentType == ContentType.AUDIOBOOK) {
            saveAudiobookProgress(book.id, track.id, playbackClient.currentPositionMs())
        }
    }

    private fun maybeSaveProgress(positionMs: Long) {
        val book = _uiState.value.book ?: return
        if (book.contentType != ContentType.AUDIOBOOK) return
        val track = currentTrack ?: return
        val now = System.currentTimeMillis()
        if (now - lastProgressSaveMs < 15_000) return
        lastProgressSaveMs = now
        saveAudiobookProgress(book.id, track.id, positionMs)
    }

    private fun saveAudiobookProgress(bookId: String, trackId: String, positionMs: Long) {
        viewModelScope.launch {
            val progress = AudiobookProgress(
                bookId = bookId,
                trackId = trackId,
                positionMs = positionMs,
                updatedAtEpochMs = System.currentTimeMillis(),
            )
            val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
            progressSyncRepository.saveLocal(progress, pendingSync = true, session?.accessToken)
        }
    }
}
