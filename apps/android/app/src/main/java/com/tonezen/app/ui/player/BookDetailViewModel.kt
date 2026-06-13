package com.tonezen.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicShuffleQueue
import com.tonezen.app.domain.progress.isBookFullyListened
import com.tonezen.app.domain.progress.resolveAudiobookPlaybackStartMs
import com.tonezen.app.playback.MusicPlaybackQueue
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
    private val localLibraryNotifier: LocalLibraryNotifier,
    private val musicPlaybackQueue: MusicPlaybackQueue,
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
                val book = _uiState.value.book
                val isCurrentBookTrack = book != null &&
                    snapshot.trackId != null &&
                    _uiState.value.tracks.any { it.id == snapshot.trackId }
                _uiState.update {
                    it.copy(
                        playbackPositionMs = if (isCurrentBookTrack && snapshot.isPlaying) {
                            snapshot.positionMs
                        } else {
                            0L
                        },
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
            val (tracks, progress) = withContext(Dispatchers.IO) {
                catalogRepository.getTracksForBook(book.id) to
                    catalogRepository.getProgress(book.id)
            }
            val syncStatus = resolveSyncStatus(book, progress)
            _uiState.update {
                it.copy(
                    book = book,
                    tracks = tracks,
                    audiobookProgress = progress,
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
                    val queue = playbackQueueBuilder.buildQueueFromLocalTracks(book, tracks)
                    if (queue.isEmpty()) return@launch
                    val startIndex = queue.indexOfFirst { it.trackId == track.id }
                    if (startIndex < 0) return@launch
                    val progress = catalogRepository.getProgress(book.id)
                    val startMs = resolveAudiobookPlaybackStartMs(progress, track)
                    currentTrack = track
                    playbackClient.playQueue(queue, startIndex, startMs)
                }
                ContentType.MUSIC -> {
                    val libraryTracks = withContext(Dispatchers.IO) {
                        val catalog = catalogRepository.resolveMusicLibraryTracks()
                        MusicShuffleQueue.order(catalog, track.id)
                    }
                    musicPlaybackQueue.set(libraryTracks)
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
            } catch (_: Exception) {
                _uiState.update { it.copy(error = DOWNLOAD_FAILED_ERROR, downloadProgress = null) }
            }
        }
    }

    fun deleteLocalDownloads() {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            val snapshot = playbackClient.snapshot.value
            val isCurrentBook = snapshot.trackId != null &&
                _uiState.value.tracks.any { it.id == snapshot.trackId }
            if (isCurrentBook) {
                playbackClient.stopAndRelease()
                _uiState.update { it.copy(activeTrackId = null, playbackPositionMs = 0L) }
            }
            catalogRepository.clearLocalDownloads(book.id)
            val tracks = catalogRepository.getTracksForBook(book.id)
            tracks.forEach { track ->
                downloadRepository.deleteLocalTrack(book.id, track.id)
            }
            localLibraryNotifier.notifyLocalLibraryChanged()
            loadBook(book)
        }
    }

    fun removeTrackDownload(track: Track) {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            downloadRepository.deleteLocalTrack(book.id, track.id)
            catalogRepository.clearTrackLocalPath(book.id, track.id)
            localLibraryNotifier.notifyLocalLibraryChanged()
            loadBook(book)
        }
    }

    fun toggleBookListened() {
        val book = _uiState.value.book ?: return
        if (book.contentType != ContentType.AUDIOBOOK) return
        if (isBookFullyListened(_uiState.value.tracks, _uiState.value.audiobookProgress)) {
            markBookUnlistened()
        } else {
            markBookListened()
        }
    }

    fun markBookListened() {
        val book = _uiState.value.book ?: return
        if (book.contentType != ContentType.AUDIOBOOK) return
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                catalogRepository.getTracksForBook(book.id).sortedBy { it.sortOrder }
            }
            val lastTrack = tracks.lastOrNull() ?: return@launch
            persistAudiobookProgress(book.id, lastTrack.id, lastTrack.durationMs ?: 0L)
        }
    }

    fun markBookUnlistened() {
        val book = _uiState.value.book ?: return
        if (book.contentType != ContentType.AUDIOBOOK) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                catalogRepository.clearProgress(book.id)
            }
            _uiState.update {
                it.copy(
                    audiobookProgress = null,
                    syncStatus = SyncDisplayStatus.NONE,
                )
            }
        }
    }

    fun markTrackListened(track: Track) {
        val book = _uiState.value.book ?: return
        if (book.contentType != ContentType.AUDIOBOOK) return
        viewModelScope.launch {
            persistAudiobookProgress(book.id, track.id, track.durationMs ?: 0L)
        }
    }

    fun markTrackUnlistened(track: Track) {
        val book = _uiState.value.book ?: return
        if (book.contentType != ContentType.AUDIOBOOK) return
        viewModelScope.launch {
            persistAudiobookProgress(book.id, track.id, 0L)
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
            persistAudiobookProgress(bookId, trackId, positionMs)
        }
    }

    private suspend fun persistAudiobookProgress(bookId: String, trackId: String, positionMs: Long) {
        val progress = AudiobookProgress(
            bookId = bookId,
            trackId = trackId,
            positionMs = positionMs,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
        progressSyncRepository.saveLocal(progress, pendingSync = true, session?.accessToken)
        _uiState.update {
            it.copy(
                audiobookProgress = progress,
                syncStatus = SyncDisplayStatus.PENDING,
            )
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

    companion object {
        const val DOWNLOAD_FAILED_ERROR = "__book_download_failed__"
    }
}
