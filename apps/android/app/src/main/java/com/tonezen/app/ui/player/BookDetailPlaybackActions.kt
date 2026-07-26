package com.tonezen.app.ui.player

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
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
    private val sessionRepository: SessionRepository,
    private val progressSyncRepository: ProgressSyncRepository,
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

    private fun formatProgressLabel(tracks: List<Track>, trackId: String, positionMs: Long): String {
        val title = tracks.find { it.id == trackId }?.title ?: "Глава"
        val totalSec = (positionMs / 1000L).coerceAtLeast(0L)
        val min = totalSec / 60L
        val sec = totalSec % 60L
        return "$title · $min:${sec.toString().padStart(2, '0')}"
    }

    fun playTrack(track: Track, skipSyncConflictPrompt: Boolean = false) {
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
                        val intent = resolveAudiobookPlaybackIntent(
                            tracks,
                            progress,
                            targetTrack,
                            skipSyncConflictPrompt = skipSyncConflictPrompt,
                        )
                    ) {
                        is AudiobookPlaybackIntent.ConfirmProgressSyncConflict -> {
                            uiState.update {
                                it.copy(
                                    confirmProgressSyncConflict = ConfirmProgressSyncConflictPrompt(
                                        pendingTrack = targetTrack,
                                        localLabel = formatProgressLabel(
                                            tracks,
                                            intent.localTrackId,
                                            intent.localPositionMs,
                                        ),
                                        serverLabel = formatProgressLabel(
                                            tracks,
                                            intent.server.trackId,
                                            intent.server.positionMs,
                                        ),
                                    ),
                                )
                            }
                            return@launch
                        }
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

    fun dismissProgressSyncConflictPrompt() {
        uiState.update { it.copy(confirmProgressSyncConflict = null) }
    }

    fun chooseProgressSyncLocal() {
        val prompt = uiState.value.confirmProgressSyncConflict ?: return
        val book = uiState.value.book ?: return
        val track = prompt.pendingTrack ?: return
        uiState.update { it.copy(confirmProgressSyncConflict = null) }
        scope.launch {
            val session = withContext(Dispatchers.IO) { sessionRepository.loadSession() }
            withContext(Dispatchers.IO) {
                progressSyncRepository.chooseLocalProgress(book.id, session?.accessToken)
            }
            playTrack(track, skipSyncConflictPrompt = true)
        }
    }

    fun chooseProgressSyncServer() {
        val book = uiState.value.book ?: return
        uiState.update { it.copy(confirmProgressSyncConflict = null) }
        scope.launch {
            val applied = withContext(Dispatchers.IO) {
                progressSyncRepository.chooseServerProgress(book.id)
            } ?: return@launch
            val tracks = withContext(Dispatchers.IO) {
                catalogRepository.getTracksForBook(book.id).sortedBy { it.sortOrder }
            }
            val track = tracks.find { it.id == applied.trackId } ?: tracks.firstOrNull() ?: return@launch
            executor.playAudiobookTrack(book, tracks, track, applied.positionMs)
        }
    }

    fun continueListening() {
        val state = uiState.value
        val progress = state.audiobookProgress
        val tracks = state.tracks.sortedBy { it.sortOrder }
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
