package com.tonezen.app.ui.player

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.progress.AudiobookPlaybackIntent
import com.tonezen.app.domain.progress.resolveAudiobookPlaybackIntent
import com.tonezen.app.playback.MusicPlaybackQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackQueueBuilder
import com.tonezen.app.playback.TrackDownloadQueueController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Плейбек-действия для экрана книги: выбор главы/трека, пауза/резюм, перемотка. */
internal class BookDetailPlaybackActions(
    private val uiState: MutableStateFlow<BookDetailUiState>,
    private val scope: CoroutineScope,
    private val catalogRepository: CatalogRepository,
    private val playbackClient: PlaybackClient,
    playbackQueueBuilder: PlaybackQueueBuilder,
    trackDownloadEnsurer: TrackDownloadEnsurer,
    networkMonitor: NetworkMonitor,
    downloadQueueController: TrackDownloadQueueController,
    localLibraryNotifier: LocalLibraryNotifier,
    musicPlaybackQueue: MusicPlaybackQueue,
    loadBook: (Book) -> Unit,
) {
    var currentTrack: Track? = null

    private val executor = BookDetailPlaybackExecutor(
        uiState = uiState,
        catalogRepository = catalogRepository,
        playbackClient = playbackClient,
        playbackQueueBuilder = playbackQueueBuilder,
        trackDownloadEnsurer = trackDownloadEnsurer,
        networkMonitor = networkMonitor,
        downloadQueueController = downloadQueueController,
        localLibraryNotifier = localLibraryNotifier,
        musicPlaybackQueue = musicPlaybackQueue,
        loadBook = loadBook,
        onTrackStarted = { currentTrack = it },
    )

    /** Подписывается на текущий трек и снапшот плеера, обновляя [uiState]. */
    fun startObservers() {
        scope.launch {
            playbackClient.activeTrackId.collect { trackId ->
                val track = uiState.value.tracks.find { it.id == trackId }
                currentTrack = track
                uiState.update { it.copy(activeTrackId = track?.id) }
            }
        }
        scope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
                val playbackState = resolveBookDetailPlaybackState(uiState.value.tracks, snapshot)
                uiState.update {
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
    }

    fun playTrack(track: Track) {
        val book = uiState.value.book ?: return
        scope.launch {
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
                            uiState.update {
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
                            executor.playAudiobookTrack(book, tracks, targetTrack, intent.positionMs)
                        AudiobookPlaybackIntent.StartFromZero ->
                            executor.playAudiobookTrack(book, tracks, targetTrack, 0L)
                    }
                }
                ContentType.MUSIC -> executor.playMusicTrack(book, track)
            }
        }
    }

    fun confirmEarlierChapterPlayback() {
        val prompt = uiState.value.confirmEarlierChapter ?: return
        uiState.update { it.copy(confirmEarlierChapter = null) }
        val book = uiState.value.book ?: return
        scope.launch {
            val tracks = withContext(Dispatchers.IO) {
                catalogRepository.getTracksForBook(book.id).sortedBy { it.sortOrder }
            }
            executor.playAudiobookTrack(book, tracks, prompt.track, 0L)
        }
    }

    fun dismissEarlierChapterPrompt() {
        uiState.update { it.copy(confirmEarlierChapter = null) }
    }

    fun continueListening() {
        val state = uiState.value
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
        if (!uiState.value.isPlaybackActiveForBook) return
        if (uiState.value.isPlaying) {
            playbackClient.pause()
        } else {
            playbackClient.play()
        }
    }

    fun seekBy(deltaMs: Long) {
        if (!uiState.value.isPlaybackActiveForBook) return
        playbackClient.seekBy(deltaMs)
    }

    fun seekToFraction(fraction: Float) {
        val durationMs = uiState.value.playbackDurationMs
        if (!uiState.value.isPlaybackActiveForBook || durationMs <= 0L) return
        playbackClient.seekTo((durationMs * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun clearPlaybackError() {
        uiState.update { it.copy(playbackErrorMessage = null) }
    }
}
