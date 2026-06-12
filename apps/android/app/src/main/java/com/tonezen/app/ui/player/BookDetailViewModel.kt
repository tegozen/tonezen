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
import com.tonezen.app.domain.music.MusicShuffleQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackEvents
import com.tonezen.app.playback.PlaybackQueueBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val sessionRepository: SessionRepository,
    private val downloadRepository: DownloadRepository,
    private val progressSyncRepository: ProgressSyncRepository,
    private val playbackClient: PlaybackClient,
    private val playbackEvents: PlaybackEvents,
    private val playbackQueueBuilder: PlaybackQueueBuilder,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private var currentTrack: Track? = null
    private var lastProgressSaveMs = 0L

    init {
        viewModelScope.launch {
            playbackEvents.trackEnded.collect { onPlaybackEnded() }
        }
        viewModelScope.launch {
            playbackClient.activeTrackId.collect { trackId ->
                val track = _uiState.value.tracks.find { it.id == trackId }
                currentTrack = track
                _uiState.update { it.copy(activeTrackId = trackId) }
            }
        }
        viewModelScope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
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
            val syncStatus = resolveSyncStatus(book, progress)
            _uiState.update {
                it.copy(
                    book = book,
                    tracks = tracks,
                    syncStatus = syncStatus,
                    estimatedDownloadBytes = estimateDownloadBytes(tracks.size),
                )
            }
        }
    }

    fun playTrack(track: Track) {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            when (book.contentType) {
                ContentType.AUDIOBOOK -> {
                    val tracks = catalogRepository.getTracksForBook(book.id).sortedBy { it.sortOrder }
                    val queue = playbackQueueBuilder.buildAudiobookQueue(book, tracks)
                    if (queue.isEmpty()) return@launch
                    val startIndex = queue.indexOfFirst { it.trackId == track.id }.coerceAtLeast(0)
                    val progress = catalogRepository.getProgress(book.id)
                    val startMs = if (progress?.trackId == track.id) progress.positionMs else 0L
                    currentTrack = track
                    playbackClient.playQueue(queue, startIndex, startMs)
                }
                ContentType.MUSIC -> {
                    val libraryTracks = withContext(Dispatchers.IO) {
                        val catalog = catalogRepository.resolveMusicLibraryTracks()
                        MusicShuffleQueue.order(catalog, track.id)
                    }
                    val target = libraryTracks.find { it.track.id == track.id } ?: return@launch
                    withContext(Dispatchers.IO) {
                        playbackQueueBuilder.buildSingleMusicItem(target.book, track)
                    } ?: return@launch
                    val queue = withContext(Dispatchers.IO) {
                        playbackQueueBuilder.buildLocalMusicLibraryQueue(libraryTracks) { entry ->
                            catalogRepository.getTracksForBook(entry.book.id)
                                .find { it.id == entry.track.id }
                                ?.takeIf { !it.localPath.isNullOrBlank() }
                        }
                    }
                    if (queue.isEmpty()) return@launch
                    val startIndex = queue.indexOfFirst { it.trackId == track.id }.coerceAtLeast(0)
                    currentTrack = track
                    playbackClient.playQueue(queue, startIndex)
                }
            }
            _uiState.update { it.copy(activeTrackId = track.id) }
            loadBook(book)
        }
    }

    fun requestDownload() {
        _uiState.update { it.copy(showDownloadSheet = true) }
    }

    fun dismissDownloadSheet() {
        _uiState.update { it.copy(showDownloadSheet = false) }
    }

    fun downloadBook() {
        val book = _uiState.value.book ?: return
        _uiState.update { it.copy(showDownloadSheet = false) }
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

    fun removeTrackDownload() {
        val book = _uiState.value.book ?: return
        val track = _uiState.value.actionTrack ?: return
        viewModelScope.launch {
            downloadRepository.deleteLocalTrack(book.id, track.id)
            catalogRepository.clearTrackLocalPath(book.id, track.id)
            loadBook(book)
            dismissTrackActions()
        }
    }

    fun showTrackActions(track: Track) {
        _uiState.update { it.copy(showTrackActions = true, actionTrack = track) }
    }

    fun dismissTrackActions() {
        _uiState.update { it.copy(showTrackActions = false, actionTrack = null) }
    }

    fun playNextTrack() {
        val book = _uiState.value.book ?: return
        val current = currentTrack ?: return
        viewModelScope.launch {
            val tracks = catalogRepository.getTracksForBook(book.id).sortedBy { it.sortOrder }
            val next = tracks.dropWhile { it.id != current.id }.drop(1).firstOrNull() ?: return@launch
            playTrack(next)
            dismissTrackActions()
        }
    }

    fun markTrackComplete() {
        val book = _uiState.value.book ?: return
        val track = _uiState.value.actionTrack ?: return
        if (book.contentType != ContentType.AUDIOBOOK) {
            dismissTrackActions()
            return
        }
        viewModelScope.launch {
            saveAudiobookProgress(book.id, track.id, track.durationMs ?: 0L)
            dismissTrackActions()
        }
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
            val book = _uiState.value.book
            if (book != null) {
                _uiState.update { it.copy(syncStatus = SyncDisplayStatus.PENDING) }
            }
        }
    }

    private suspend fun resolveSyncStatus(book: Book, progress: AudiobookProgress?): SyncDisplayStatus {
        if (book.contentType != ContentType.AUDIOBOOK) return SyncDisplayStatus.NONE
        if (progress == null) return SyncDisplayStatus.NONE
        return if (catalogRepository.isProgressPendingSync(book.id)) {
            SyncDisplayStatus.PENDING
        } else {
            SyncDisplayStatus.SYNCED
        }
    }

    private fun estimateDownloadBytes(trackCount: Int): Long = trackCount * 32L * 1024L * 1024L
}
