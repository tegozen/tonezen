package com.tonezen.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.R
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
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
    private val playbackQueueBuilder: PlaybackQueueBuilder,
    private val trackDownloadEnsurer: TrackDownloadEnsurer,
    private val localLibraryNotifier: LocalLibraryNotifier,
    private val musicPlaybackQueue: MusicPlaybackQueue,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private var currentTrack: Track? = null

    init {
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
                    val tracks = withContext(Dispatchers.IO) {
                        catalogRepository.getTracksForBook(book.id).sortedBy { it.sortOrder }
                    }
                    val targetTrack = tracks.find { it.id == track.id } ?: return@launch
                    _uiState.update { it.copy(playbackErrorRes = null) }
                    val localTrack = if (!targetTrack.localPath.isNullOrBlank()) {
                        targetTrack
                    } else {
                        val outcome = withContext(Dispatchers.IO) {
                            trackDownloadEnsurer.ensureTrackLocal(book.id, targetTrack) { progress ->
                                _uiState.update { it.copy(downloadProgress = progress) }
                            }
                        }
                        _uiState.update { it.copy(downloadProgress = null) }
                        if (outcome.track == null) {
                            _uiState.update {
                                it.copy(
                                    playbackErrorRes = playbackErrorRes(outcome.failure),
                                    showDownloadSheet = outcome.failure == EnsureTrackOutcome.Failure.DOWNLOAD_FAILED,
                                )
                            }
                            return@launch
                        }
                        localLibraryNotifier.notifyLocalLibraryChanged()
                        outcome.track
                    }
                    val refreshedTracks = withContext(Dispatchers.IO) {
                        catalogRepository.getTracksForBook(book.id).sortedBy { it.sortOrder }
                    }
                    val queue = playbackQueueBuilder.buildQueueFromLocalTracks(book, refreshedTracks)
                    if (queue.isEmpty()) return@launch
                    val startIndex = queue.indexOfFirst { it.trackId == localTrack.id }
                    if (startIndex < 0) return@launch
                    val progress = catalogRepository.getProgress(book.id)
                    val startMs = resolveAudiobookPlaybackStartMs(progress, localTrack)
                    currentTrack = localTrack
                    playbackClient.playQueue(queue, startIndex, startMs)
                }
                ContentType.MUSIC -> {
                    val libraryTracks = withContext(Dispatchers.IO) {
                        val catalog = catalogRepository.resolveMusicLibraryTracks()
                        MusicShuffleQueue.order(catalog, track.id)
                    }
                    musicPlaybackQueue.set(libraryTracks)
                    val target = libraryTracks.find { it.track.id == track.id } ?: return@launch
                    val localTrack = withContext(Dispatchers.IO) {
                        trackDownloadEnsurer.ensureTrackLocal(target.book.id, track).track
                    } ?: run {
                        _uiState.update {
                            it.copy(playbackErrorRes = R.string.music_playback_error_download)
                        }
                        return@launch
                    }
                    val queue = withContext(Dispatchers.IO) {
                        playbackQueueBuilder.buildLocalMusicLibraryQueue(libraryTracks) { entry ->
                            if (entry.track.id == localTrack.id) {
                                localTrack
                            } else {
                                trackDownloadEnsurer.resolveLocalTrack(entry.book.id, entry.track)
                            }
                        }
                    }
                    if (queue.isEmpty()) {
                        _uiState.update {
                            it.copy(playbackErrorRes = R.string.music_playback_error_download)
                        }
                        return@launch
                    }
                    val startIndex = queue.indexOfFirst { it.trackId == localTrack.id }.coerceAtLeast(0)
                    currentTrack = localTrack
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

    fun clearPlaybackError() {
        _uiState.update { it.copy(playbackErrorRes = null) }
    }

    fun clearDownloadError() {
        _uiState.update { it.copy(error = null) }
    }

    fun continueListening() {
        val progress = _uiState.value.audiobookProgress ?: return
        val track = _uiState.value.tracks.find { it.id == progress.trackId } ?: return
        playTrack(track)
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

    private fun playbackErrorRes(failure: EnsureTrackOutcome.Failure?): Int = when (failure) {
        EnsureTrackOutcome.Failure.OFFLINE -> R.string.music_playback_error_offline
        EnsureTrackOutcome.Failure.NO_SESSION -> R.string.music_playback_error_login
        EnsureTrackOutcome.Failure.DOWNLOAD_FAILED, null -> R.string.music_playback_error_download
    }

    private fun estimateDownloadBytes(trackCount: Int): Long = trackCount * 32L * 1024L * 1024L

    companion object {
        const val DOWNLOAD_FAILED_ERROR = "__book_download_failed__"
    }
}
