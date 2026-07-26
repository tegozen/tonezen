package com.tonezen.app.ui.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.MusicPlaybackQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackEvents
import com.tonezen.app.playback.PlaybackQueueBuilder
import com.tonezen.app.playback.TrackDownloadQueueController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Facade ViewModel for the book detail screen.
 * Business logic is delegated to [BookDetailPlaybackActions], [BookDetailDownloadActions]
 * and [BookDetailProgressActions]; this class owns the shared [BookDetailUiState] and wires
 * their observers plus book/track loading.
 */
@HiltViewModel
class BookDetailViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    sessionRepository: SessionRepository,
    downloadRepository: DownloadRepository,
    progressSyncRepository: ProgressSyncRepository,
    private val playbackClient: PlaybackClient,
    playbackQueueBuilder: PlaybackQueueBuilder,
    trackDownloadEnsurer: TrackDownloadEnsurer,
    networkMonitor: NetworkMonitor,
    downloadQueueController: TrackDownloadQueueController,
    downloadQueueNotifier: DownloadQueueNotifier,
    private val localLibraryNotifier: LocalLibraryNotifier,
    musicPlaybackQueue: MusicPlaybackQueue,
    playbackEvents: PlaybackEvents,
) : ViewModel() {
    private val _uiState = MutableStateFlow(BookDetailUiState())
    val uiState: StateFlow<BookDetailUiState> = _uiState.asStateFlow()
    private val _playbackProgress = MutableStateFlow(BookDetailPlaybackProgress())
    val playbackProgress: StateFlow<BookDetailPlaybackProgress> = _playbackProgress.asStateFlow()
    private val _trackDownloads = MutableStateFlow<Map<String, BookDetailTrackDownloadUi>>(emptyMap())
    val trackDownloads: StateFlow<Map<String, BookDetailTrackDownloadUi>> = _trackDownloads.asStateFlow()

    private val progressActions = BookDetailProgressActions(
        uiState = _uiState,
        playbackProgress = _playbackProgress,
        scope = viewModelScope,
        catalogRepository = catalogRepository,
        sessionRepository = sessionRepository,
        progressSyncRepository = progressSyncRepository,
        playbackEvents = playbackEvents,
    )
    private val playbackActions = BookDetailPlaybackActions(
        uiState = _uiState,
        playbackProgress = _playbackProgress,
        scope = viewModelScope,
        catalogRepository = catalogRepository,
        sessionRepository = sessionRepository,
        progressSyncRepository = progressSyncRepository,
        playbackClient = playbackClient,
        playbackQueueBuilder = playbackQueueBuilder,
        trackDownloadEnsurer = trackDownloadEnsurer,
        networkMonitor = networkMonitor,
        downloadQueueController = downloadQueueController,
        localLibraryNotifier = localLibraryNotifier,
        musicPlaybackQueue = musicPlaybackQueue,
        loadBook = ::loadBook,
    )
    private val downloadActions = BookDetailDownloadActions(
        uiState = _uiState,
        trackDownloads = _trackDownloads,
        playbackProgress = _playbackProgress,
        scope = viewModelScope,
        catalogRepository = catalogRepository,
        downloadRepository = downloadRepository,
        networkMonitor = networkMonitor,
        downloadQueueController = downloadQueueController,
        downloadQueueNotifier = downloadQueueNotifier,
        localLibraryNotifier = localLibraryNotifier,
        playbackClient = playbackClient,
        loadBook = ::loadBook,
    )

    init {
        playbackActions.startObservers()
        downloadActions.startObserving()
        progressActions.startObserving()
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
            val syncStatus = progressActions.resolveSyncStatus(book, progress)
            val playbackState = resolveBookDetailPlaybackState(tracks, playbackClient.snapshot.value)
            _playbackProgress.value = BookDetailPlaybackProgress(
                positionMs = playbackState.positionMs,
                durationMs = playbackState.durationMs,
            )
            _uiState.update {
                it.copy(
                    book = book,
                    tracks = tracks,
                    audiobookProgress = progress,
                    syncStatus = syncStatus,
                    activeTrackId = playbackState.activeTrackId ?: it.activeTrackId,
                    isPlaying = playbackState.isPlaying || it.isPlaying,
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
        _playbackProgress.value = BookDetailPlaybackProgress(
            positionMs = playbackState.positionMs,
            durationMs = playbackState.durationMs,
        )
        _uiState.update {
            it.copy(
                tracks = tracks,
                activeTrackId = playbackState.activeTrackId ?: it.activeTrackId,
                isPlaying = playbackState.isPlaying || it.isPlaying,
                isPlaybackActiveForBook = playbackState.isActiveForBook || it.isPlaybackActiveForBook,
            )
        }
    }

    fun playTrack(track: Track) = playbackActions.playTrack(track)

    fun confirmEarlierChapterPlayback() = playbackActions.confirmEarlierChapterPlayback()

    fun dismissEarlierChapterPrompt() = playbackActions.dismissEarlierChapterPrompt()

    fun confirmEarlierCycleBookPlayback() = playbackActions.confirmEarlierCycleBookPlayback()

    fun dismissEarlierCycleBookPrompt() = playbackActions.dismissEarlierCycleBookPrompt()

    fun dismissProgressSyncConflictPrompt() = playbackActions.dismissProgressSyncConflictPrompt()

    fun chooseProgressSyncLocal() = playbackActions.chooseProgressSyncLocal()

    fun chooseProgressSyncServer() = playbackActions.chooseProgressSyncServer()

    fun continueListening() = playbackActions.continueListening()

    fun pauseOrResume() = playbackActions.pauseOrResume()

    fun seekBy(deltaMs: Long) = playbackActions.seekBy(deltaMs)

    fun seekToFraction(fraction: Float) = playbackActions.seekToFraction(fraction)

    fun clearPlaybackError() = playbackActions.clearPlaybackError()

    fun requestDownload() = downloadActions.requestDownload()

    fun requestTrackDownload(track: Track) = downloadActions.requestTrackDownload(track)

    fun clearDownloadError() = downloadActions.clearDownloadError()

    fun deleteLocalDownloads() = downloadActions.deleteLocalDownloads()

    fun removeTrackDownload(track: Track) = downloadActions.removeTrackDownload(track)

    fun toggleBookListened() = progressActions.toggleBookListened()

    fun markBookListened() = progressActions.markBookListened()

    fun markBookUnlistened() = progressActions.markBookUnlistened()

    fun markTrackListened(track: Track) = progressActions.markTrackListened(track)

    fun markTrackUnlistened(track: Track) = progressActions.markTrackUnlistened(track)

    companion object {
        const val DOWNLOAD_FAILED_ERROR = "__book_download_failed__"
        const val DOWNLOAD_OFFLINE_ERROR = "__book_download_offline__"
    }
}
