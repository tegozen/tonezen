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

private val PLAYBACK_SPEEDS = floatArrayOf(0.75f, 1f, 1.25f, 1.5f, 2f)

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
                        positionMs = snapshot.positionMs,
                        durationMs = snapshot.durationMs,
                        playbackSpeed = playbackClient.playbackSpeed(),
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
            val favorite = catalogRepository.isFavorite(book.id)
            val syncStatus = resolveSyncStatus(book, progress)
            _uiState.update {
                it.copy(
                    book = book,
                    tracks = tracks,
                    progressTrackTitle = progress?.let { p ->
                        tracks.find { t -> t.id == p.trackId }?.title
                    },
                    isFavorite = favorite,
                    syncStatus = syncStatus,
                    estimatedDownloadBytes = estimateDownloadBytes(tracks.size),
                )
            }
        }
    }

    fun selectTab(tab: BookDetailTab) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun playBook() {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            val tracks = catalogRepository.getTracksForBook(book.id)
            val queue = playbackQueueBuilder.buildAudiobookQueue(book, tracks)
            if (queue.isEmpty()) return@launch
            val progress = catalogRepository.getProgress(book.id)
            val startTrackId = progress?.trackId ?: queue.first().trackId
            val startIndex = queue.indexOfFirst { it.trackId == startTrackId }.coerceAtLeast(0)
            val startMs = if (queue[startIndex].trackId == progress?.trackId) progress.positionMs else 0L
            currentTrack = tracks.find { it.id == startTrackId }
            playbackClient.playQueue(queue, startIndex, startMs)
            _uiState.update { it.copy(nowPlayingTitle = currentTrack?.title) }
            loadBook(book)
        }
    }

    fun playTrack(track: Track) {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            when (book.contentType) {
                ContentType.AUDIOBOOK -> {
                    val item = playbackQueueBuilder.buildSingleAudiobookItem(book, track) ?: return@launch
                    currentTrack = track
                    playbackClient.playQueue(listOf(item), startIndex = 0)
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
            _uiState.update { it.copy(nowPlayingTitle = track.title) }
            loadBook(book)
        }
    }

    fun pausePlayback() {
        playbackClient.pause()
    }

    fun resumePlayback() {
        playbackClient.play()
    }

    fun seekTo(positionMs: Long) {
        playbackClient.seekTo(positionMs)
    }

    fun seekBy(deltaMs: Long) {
        playbackClient.seekBy(deltaMs)
    }

    fun cycleSpeed() {
        val current = playbackClient.playbackSpeed()
        val index = PLAYBACK_SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.01f }
            .takeIf { it >= 0 } ?: 1
        val next = PLAYBACK_SPEEDS[(index + 1) % PLAYBACK_SPEEDS.size]
        playbackClient.setPlaybackSpeed(next)
        _uiState.update { it.copy(playbackSpeed = next) }
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

    fun toggleFavorite() {
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            catalogRepository.toggleFavorite(book.id)
            _uiState.update { it.copy(isFavorite = catalogRepository.isFavorite(book.id)) }
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
