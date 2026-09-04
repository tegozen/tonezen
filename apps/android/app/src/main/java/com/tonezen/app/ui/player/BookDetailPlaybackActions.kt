package com.tonezen.app.ui.player

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.progress.AudiobookPlaybackIntent
import com.tonezen.app.domain.progress.findCycleContainingBook
import com.tonezen.app.domain.progress.resolveAudiobookPlaybackIntent
import com.tonezen.app.domain.progress.resolveBookContinuePlayHead
import com.tonezen.app.domain.progress.resolveEarlierCycleBookConfirm
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.ui.components.formatPlaybackProgressLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Плейбек-действия для экрана книги: выбор главы/трека, пауза/резюм, перемотка. */
internal class BookDetailPlaybackActions(
    private val uiState: MutableStateFlow<BookDetailUiState>,
    private val playbackProgress: MutableStateFlow<BookDetailPlaybackProgress>,
    private val scope: CoroutineScope,
    private val catalogRepository: CatalogRepository,
    private val playbackClient: PlaybackClient,
    private val audiobookPlayback: BookDetailPlaybackExecutor,
    private val musicPlayback: BookDetailMusicPlayback,
    private val persistAudiobookProgress: suspend (String, String, Long) -> Unit,
) {

    fun playTrack(
        track: Track,
        skipSyncConflictPrompt: Boolean = false,
        skipEarlierCyclePrompt: Boolean = false,
    ) {
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
                    val intent = resolveAudiobookPlaybackIntent(
                        tracks,
                        progress,
                        targetTrack,
                        skipSyncConflictPrompt = skipSyncConflictPrompt,
                    )
                    if (intent is AudiobookPlaybackIntent.ConfirmProgressSyncConflict) {
                        uiState.update {
                            it.copy(
                                confirmProgressSyncConflict = ConfirmProgressSyncConflictPrompt(
                                    pendingTrack = targetTrack,
                                    localLabel = formatPlaybackProgressLabel(
                                        tracks,
                                        intent.localTrackId,
                                        intent.localPositionMs,
                                    ),
                                    serverLabel = formatPlaybackProgressLabel(
                                        tracks,
                                        intent.server.trackId,
                                        intent.server.positionMs,
                                    ),
                                ),
                            )
                        }
                        return@launch
                    }
                    if (!skipEarlierCyclePrompt) {
                        val laterBook = withContext(Dispatchers.IO) {
                            val cycles = catalogRepository.getAllCycles()
                            val cycle = findCycleContainingBook(cycles, book.id) ?: return@withContext null
                            val bookIds = cycle.books.map { it.id }
                            val tracksByBookId = catalogRepository.getTracksByBookIds(bookIds)
                            val progressByBookId = catalogRepository.getProgressByBookIds(bookIds)
                            resolveEarlierCycleBookConfirm(cycle, book, tracksByBookId, progressByBookId)
                        }
                        if (laterBook != null) {
                            uiState.update {
                                it.copy(
                                    confirmEarlierCycleBook = ConfirmEarlierCycleBookPrompt(
                                        track = targetTrack,
                                        laterBookTitle = laterBook.title,
                                    ),
                                )
                            }
                            return@launch
                        }
                    }
                    when (intent) {
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
                        }
                        is AudiobookPlaybackIntent.Resume -> {
                            if (audiobookPlayback.playAudiobookTrack(book, tracks, targetTrack, intent.positionMs)) {
                                persistPlaybackStart(book.id, targetTrack.id, intent.positionMs)
                            }
                        }
                        AudiobookPlaybackIntent.StartFromZero -> {
                            if (audiobookPlayback.playAudiobookTrack(book, tracks, targetTrack, 0L)) {
                                persistPlaybackStart(book.id, targetTrack.id, 0L)
                            }
                        }
                        is AudiobookPlaybackIntent.ConfirmProgressSyncConflict -> Unit
                    }
                }
                ContentType.MUSIC -> musicPlayback.play(book, track)
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
            if (audiobookPlayback.playAudiobookTrack(book, tracks, prompt.track, 0L)) {
                persistPlaybackStart(book.id, prompt.track.id, 0L)
            }
        }
    }

    fun dismissEarlierChapterPrompt() {
        uiState.update { it.copy(confirmEarlierChapter = null) }
    }

    fun confirmEarlierCycleBookPlayback() {
        val prompt = uiState.value.confirmEarlierCycleBook ?: return
        uiState.update { it.copy(confirmEarlierCycleBook = null) }
        playTrack(prompt.track, skipEarlierCyclePrompt = true)
    }

    fun dismissEarlierCycleBookPrompt() {
        uiState.update { it.copy(confirmEarlierCycleBook = null) }
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
            if (audiobookPlayback.playAudiobookTrack(book, tracks, track, applied.positionMs)) {
                persistPlaybackStart(book.id, track.id, applied.positionMs)
            }
        }
    }

    fun continueListening() {
        val state = uiState.value
        val tracks = state.tracks.sortedBy { it.sortOrder }
        val head = resolveBookContinuePlayHead(tracks, state.audiobookProgress) ?: return
        playTrack(head.track)
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
        persistSeekProgress(playbackClient.snapshot.value.positionMs)
    }

    fun seekToFraction(fraction: Float) {
        val durationMs = playbackProgress.value.durationMs
        if (!uiState.value.isPlaybackActiveForBook || durationMs <= 0L) return
        val positionMs = (durationMs * fraction.coerceIn(0f, 1f)).toLong()
        playbackClient.seekTo(positionMs)
        persistSeekProgress(positionMs)
    }

    private fun persistSeekProgress(positionMs: Long) {
        val book = uiState.value.book ?: return
        if (book.contentType != ContentType.AUDIOBOOK) return
        val trackId = uiState.value.activeTrackId ?: return
        scope.launch {
            persistPlaybackStart(book.id, trackId, positionMs)
        }
    }

    /** Play-start must be a real head: zero remains reserved for explicit «unlistened». */
    private suspend fun persistPlaybackStart(bookId: String, trackId: String, positionMs: Long) {
        persistAudiobookProgress(bookId, trackId, positionMs.coerceAtLeast(1L))
        uiState.update { it.copy(syncStatus = SyncDisplayStatus.PENDING) }
    }

    fun clearPlaybackError() {
        uiState.update { it.copy(playbackErrorMessage = null) }
    }
}
