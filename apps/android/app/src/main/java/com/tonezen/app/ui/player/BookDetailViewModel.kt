package com.tonezen.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.domain.downloads.EnqueueDownloadRequest
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.progress.completedAudiobookProgress
import com.tonezen.app.domain.music.MusicShuffleQueue
import com.tonezen.app.domain.music.MusicQueueWindow
import com.tonezen.app.playback.PlaybackEvents
import com.tonezen.app.playback.TrackDownloadQueueController
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.domain.progress.isBookFullyListened
import com.tonezen.app.domain.progress.AudiobookPlaybackIntent
import com.tonezen.app.domain.progress.resolveAudiobookPlaybackIntent
import com.tonezen.app.playback.MusicPlaybackQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackQueueBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
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
    private val networkMonitor: NetworkMonitor,
    private val downloadQueueController: TrackDownloadQueueController,
    private val downloadQueueNotifier: DownloadQueueNotifier,
    private val localLibraryNotifier: LocalLibraryNotifier,
    private val musicPlaybackQueue: MusicPlaybackQueue,
    private val playbackEvents: PlaybackEvents,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()

    private var currentTrack: Track? = null

    init {
        viewModelScope.launch {
            playbackClient.activeTrackId.collect { trackId ->
                val track = _uiState.value.tracks.find { it.id == trackId }
                currentTrack = track
                _uiState.update { it.copy(activeTrackId = track?.id) }
            }
        }
        viewModelScope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
                val playbackState = resolveBookDetailPlaybackState(_uiState.value.tracks, snapshot)
                _uiState.update {
                    it.copy(
                        activeTrackId = playbackState.activeTrackId,
                        playbackPositionMs = playbackState.positionMs,
                        playbackDurationMs = playbackState.durationMs,
                        isPlaying = playbackState.isPlaying,
                        isPlaybackActiveForBook = playbackState.isActiveForBook,
                    )
                }
            }
        }
        viewModelScope.launch {
            downloadQueueNotifier.state.collect { queueState ->
                _uiState.update { it.copy(downloadQueueState = queueState) }
            }
        }
        viewModelScope.launch {
            playbackEvents.trackEnded.collect {
                val state = _uiState.value
                val book = state.book ?: return@collect
                if (book.contentType != ContentType.AUDIOBOOK) return@collect
                val endedTrackId = state.activeTrackId ?: return@collect
                val endedTrack = state.tracks.find { it.id == endedTrackId } ?: return@collect
                val completed = completedAudiobookProgress(
                    bookId = book.id,
                    contentType = book.contentType,
                    track = endedTrack,
                    fallbackDurationMs = state.playbackDurationMs,
                ) ?: return@collect
                persistAudiobookProgress(book.id, completed.trackId, completed.positionMs)
            }
        }
        // Обновляем треки при изменении локальной библиотеки (завершение фоновой загрузки),
        // чтобы иконки скачивания обновились на галочку без перехода назад/вперёд.
        viewModelScope.launch {
            localLibraryNotifier.changes
                .debounce(300L)
                .collect {
                    val book = _uiState.value.book ?: return@collect
                    reloadTracks(book)
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
            val playbackState = resolveBookDetailPlaybackState(tracks, playbackClient.snapshot.value)
            _uiState.update {
                it.copy(
                    book = book,
                    tracks = tracks,
                    audiobookProgress = progress,
                    syncStatus = syncStatus,
                    activeTrackId = playbackState.activeTrackId ?: it.activeTrackId,
                    isPlaybackActiveForBook = playbackState.isActiveForBook || it.isPlaybackActiveForBook,
                )
            }
        }
    }

    /** Перечитывает только треки (без полной загрузки книги) — используется при фоновых изменениях. */
    private suspend fun reloadTracks(book: Book) {
        val tracks = withContext(Dispatchers.IO) {
            catalogRepository.getTracksForBook(book.id)
        }
        val playbackState = resolveBookDetailPlaybackState(tracks, playbackClient.snapshot.value)
        _uiState.update {
            it.copy(
                tracks = tracks,
                activeTrackId = playbackState.activeTrackId ?: it.activeTrackId,
                isPlaybackActiveForBook = playbackState.isActiveForBook || it.isPlaybackActiveForBook,
            )
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
                    val progress = withContext(Dispatchers.IO) {
                        catalogRepository.getProgress(book.id)
                    }
                    when (
                        val intent = resolveAudiobookPlaybackIntent(tracks, progress, targetTrack)
                    ) {
                        is AudiobookPlaybackIntent.ConfirmEarlierChapter -> {
                            _uiState.update {
                                it.copy(
                                    confirmEarlierChapter = ConfirmEarlierChapterPrompt(
                                        track = targetTrack,
                                        savedTrackId = intent.savedTrackId,
                                        savedPositionMs = intent.savedPositionMs,
                                    ),
                                )
                            }
                            return@launch
                        }
                        is AudiobookPlaybackIntent.Resume ->
                            playAudiobookTrack(book, tracks, targetTrack, intent.positionMs)
                        AudiobookPlaybackIntent.StartFromZero ->
                            playAudiobookTrack(book, tracks, targetTrack, 0L)
                    }
                }
                ContentType.MUSIC -> playMusicTrack(book, track)
            }
        }
    }

    fun confirmEarlierChapterPlayback() {
        val prompt = _uiState.value.confirmEarlierChapter ?: return
        _uiState.update { it.copy(confirmEarlierChapter = null) }
        val book = _uiState.value.book ?: return
        viewModelScope.launch {
            val tracks = withContext(Dispatchers.IO) {
                catalogRepository.getTracksForBook(book.id).sortedBy { it.sortOrder }
            }
            playAudiobookTrack(book, tracks, prompt.track, 0L)
        }
    }

    fun dismissEarlierChapterPrompt() {
        _uiState.update { it.copy(confirmEarlierChapter = null) }
    }

    private suspend fun playAudiobookTrack(
        book: Book,
        tracks: List<Track>,
        targetTrack: Track,
        startMs: Long,
    ) {
        _uiState.update { it.copy(playbackErrorMessage = null) }
        val localTrack = if (!targetTrack.localPath.isNullOrBlank()) {
            targetTrack
        } else {
            val awaitResult = downloadQueueController.awaitTrack(
                bookId = book.id,
                trackId = targetTrack.id,
                priority = DownloadPriority.PLAY,
                title = targetTrack.title,
                subtitle = book.title,
                contentType = book.contentType.name.lowercase(),
            )
            if (awaitResult != DownloadAwaitResult.COMPLETED) {
                _uiState.update {
                    it.copy(playbackErrorMessage = playbackErrorMessage(awaitResult))
                }
                return
            }
            withContext(Dispatchers.IO) {
                trackDownloadEnsurer.resolveLocalTrack(book.id, targetTrack)
            } ?: run {
                _uiState.update {
                    it.copy(playbackErrorMessage = playbackErrorMessage(DownloadAwaitResult.FAILED))
                }
                return
            }.also {
                localLibraryNotifier.notifyLocalLibraryChanged()
            }
        }
        val refreshedTracks = withContext(Dispatchers.IO) {
            catalogRepository.getTracksForBook(book.id).sortedBy { it.sortOrder }
        }
        val tracksForQueue = refreshedTracks.map { t ->
            if (t.id == localTrack.id && t.localPath.isNullOrBlank()) localTrack else t
        }
        val queue = playbackQueueBuilder.buildQueueFromLocalTracks(book, tracksForQueue)
        if (queue.isEmpty()) return
        val startIndex = queue.indexOfFirst { it.trackId == localTrack.id }
        if (startIndex < 0) return
        currentTrack = localTrack
        playbackClient.playQueue(queue, startIndex, startMs)
        prefetchNextChapter(book, tracks, localTrack)
        _uiState.update { it.copy(activeTrackId = localTrack.id) }
        loadBook(book)
    }

    private fun prefetchNextChapter(book: Book, tracks: List<Track>, currentTrack: Track) {
        if (!networkMonitor.isOnline()) return
        val sorted = tracks.sortedBy { it.sortOrder }
        val index = sorted.indexOfFirst { it.id == currentTrack.id }
        if (index < 0 || index >= sorted.lastIndex) return
        val next = sorted[index + 1]
        if (!next.localPath.isNullOrBlank()) return
        downloadQueueController.enqueue(
            EnqueueDownloadRequest(
                bookId = book.id,
                trackId = next.id,
                priority = DownloadPriority.PREFETCH,
                title = next.title,
                subtitle = book.title,
                contentType = book.contentType.name.lowercase(),
            ),
        )
    }

    private suspend fun playMusicTrack(book: Book, track: Track) {
        val libraryTracks = withContext(Dispatchers.IO) {
            val catalog = catalogRepository.resolveMusicLibraryTracks()
            MusicShuffleQueue.order(catalog, track.id)
        }
        musicPlaybackQueue.set(libraryTracks)
        val target = libraryTracks.find { it.track.id == track.id } ?: return
        val localTrack = withContext(Dispatchers.IO) {
            trackDownloadEnsurer.ensureTrackLocal(target.book.id, track).track
        } ?: run {
            _uiState.update {
                it.copy(playbackErrorMessage = "Не удалось скачать трек")
            }
            return
        }
        val queueWindow = MusicQueueWindow.initialWindow(
            items = libraryTracks,
            startTrackId = localTrack.id,
            idOf = { it.track.id },
        )
        val queue = withContext(Dispatchers.IO) {
            playbackQueueBuilder.buildLocalMusicLibraryQueue(queueWindow) { entry ->
                if (entry.track.id == localTrack.id) {
                    localTrack
                } else {
                    trackDownloadEnsurer.resolveLocalTrack(entry.book.id, entry.track)
                }
            }
        }
        if (queue.isEmpty()) {
            _uiState.update {
                it.copy(playbackErrorMessage = "Не удалось скачать трек")
            }
            return
        }
        val startIndex = queue.indexOfFirst { it.trackId == localTrack.id }.coerceAtLeast(0)
        currentTrack = localTrack
        playbackClient.playQueue(queue, startIndex)
        _uiState.update { it.copy(activeTrackId = track.id) }
        loadBook(book)
    }

    fun requestDownload() {
        downloadAllMissingTracks()
    }

    fun requestTrackDownload(track: Track) {
        val book = _uiState.value.book ?: return
        if (!track.localPath.isNullOrBlank()) return
        if (!networkMonitor.isOnline()) {
            _uiState.update { it.copy(error = DOWNLOAD_OFFLINE_ERROR) }
            return
        }
        downloadQueueController.enqueue(
            EnqueueDownloadRequest(
                bookId = book.id,
                trackId = track.id,
                priority = DownloadPriority.USER,
                title = track.title,
                subtitle = book.title,
                contentType = book.contentType.name.lowercase(),
            ),
        )
    }

    fun clearPlaybackError() {
        _uiState.update { it.copy(playbackErrorMessage = null) }
    }

    fun clearDownloadError() {
        _uiState.update { it.copy(error = null) }
    }

    fun continueListening() {
        val state = _uiState.value
        val progress = state.audiobookProgress
        val tracks = state.tracks.sortedBy { it.sortOrder }
        // Если прогресса нет — начинаем с первого трека
        val track = if (progress != null) {
            tracks.find { it.id == progress.trackId } ?: tracks.firstOrNull()
        } else {
            tracks.firstOrNull()
        } ?: return
        playTrack(track)
    }

    fun pauseOrResume() {
        if (!_uiState.value.isPlaybackActiveForBook) return
        if (_uiState.value.isPlaying) {
            playbackClient.pause()
        } else {
            playbackClient.play()
        }
    }

    fun seekBy(deltaMs: Long) {
        if (!_uiState.value.isPlaybackActiveForBook) return
        playbackClient.seekBy(deltaMs)
    }

    fun seekToFraction(fraction: Float) {
        val durationMs = _uiState.value.playbackDurationMs
        if (!_uiState.value.isPlaybackActiveForBook || durationMs <= 0L) return
        playbackClient.seekTo((durationMs * fraction.coerceIn(0f, 1f)).toLong())
    }

    private fun downloadAllMissingTracks() {
        val book = _uiState.value.book ?: return
        val missingTracks = _uiState.value.tracks
            .sortedBy { it.sortOrder }
            .filter { it.localPath.isNullOrBlank() }
        if (missingTracks.isEmpty()) return
        val batchId = java.util.UUID.randomUUID().toString()
        val requests = missingTracks.map { track ->
            EnqueueDownloadRequest(
                bookId = book.id,
                trackId = track.id,
                priority = DownloadPriority.USER,
                batchId = batchId,
                title = track.title,
                subtitle = book.title,
                contentType = book.contentType.name.lowercase(),
            )
        }
        downloadQueueController.enqueueBatch(requests, batchId)
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

    private fun playbackErrorMessage(failure: EnsureTrackOutcome.Failure?): String = when (failure) {
        EnsureTrackOutcome.Failure.OFFLINE -> "Нет сети — нужен интернет для первой загрузки"
        EnsureTrackOutcome.Failure.NO_SESSION -> "Войдите в аккаунт, чтобы скачать трек"
        EnsureTrackOutcome.Failure.DOWNLOAD_FAILED, null -> "Не удалось скачать трек"
    }

    private fun playbackErrorMessage(result: DownloadAwaitResult): String = when (result) {
        DownloadAwaitResult.OFFLINE -> "Нет сети — нужен интернет для первой загрузки"
        DownloadAwaitResult.FAILED -> "Не удалось скачать трек"
        DownloadAwaitResult.CANCELLED, DownloadAwaitResult.COMPLETED -> "Не удалось скачать трек"
    }

    companion object {
        const val DOWNLOAD_FAILED_ERROR = "__book_download_failed__"
        const val DOWNLOAD_OFFLINE_ERROR = "__book_download_offline__"
    }
}
