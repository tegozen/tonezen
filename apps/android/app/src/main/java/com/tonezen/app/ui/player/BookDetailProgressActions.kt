package com.tonezen.app.ui.player

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.progress.completedAudiobookProgress
import com.tonezen.app.domain.progress.isBookFullyListened
import com.tonezen.app.playback.PlaybackEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Отметки "прослушано"/синхронизация прогресса аудиокниги на экране книги. */
internal class BookDetailProgressActions(
    private val uiState: MutableStateFlow<BookDetailUiState>,
    private val playbackProgress: MutableStateFlow<BookDetailPlaybackProgress>,
    private val scope: CoroutineScope,
    private val catalogRepository: CatalogRepository,
    private val sessionRepository: SessionRepository,
    private val progressSyncRepository: ProgressSyncRepository,
    private val playbackEvents: PlaybackEvents,
) {
    /** Сохраняет прогресс аудиокниги при завершении трека. */
    fun startObserving() {
        scope.launch {
            playbackEvents.trackEnded.collect {
                val state = uiState.value
                val book = state.book ?: return@collect
                if (book.contentType != ContentType.AUDIOBOOK) return@collect
                val endedTrackId = state.activeTrackId ?: return@collect
                val endedTrack = state.tracks.find { it.id == endedTrackId } ?: return@collect
                val completed = completedAudiobookProgress(
                    bookId = book.id,
                    contentType = book.contentType,
                    track = endedTrack,
                    fallbackDurationMs = playbackProgress.value.durationMs,
                ) ?: return@collect
                persistAudiobookProgress(book.id, completed.trackId, completed.positionMs)
            }
        }
    }

    fun toggleBookListened() {
        val book = uiState.value.book ?: return
        if (book.contentType != ContentType.AUDIOBOOK) return
        if (isBookFullyListened(uiState.value.tracks, uiState.value.audiobookProgress)) {
            markBookUnlistened()
        } else {
            markBookListened()
        }
    }

    fun markBookListened() {
        val book = uiState.value.book ?: return
        if (book.contentType != ContentType.AUDIOBOOK) return
        scope.launch {
            val tracks = withContext(Dispatchers.IO) {
                catalogRepository.getTracksForBook(book.id).sortedBy { it.sortOrder }
            }
            val lastTrack = tracks.lastOrNull() ?: return@launch
            persistAudiobookProgress(book.id, lastTrack.id, lastTrack.durationMs ?: 0L)
        }
    }

    fun markBookUnlistened() {
        val book = uiState.value.book ?: return
        if (book.contentType != ContentType.AUDIOBOOK) return
        scope.launch {
            val tracks = withContext(Dispatchers.IO) {
                catalogRepository.getTracksForBook(book.id).sortedBy { it.sortOrder }
            }
            val firstTrack = tracks.firstOrNull() ?: return@launch
            // Reset via synced play head (pos 0) — local delete alone is restored by pull.
            persistAudiobookProgress(book.id, firstTrack.id, 0L)
        }
    }

    fun markTrackListened(track: Track) {
        val book = uiState.value.book ?: return
        if (book.contentType != ContentType.AUDIOBOOK) return
        scope.launch {
            persistAudiobookProgress(book.id, track.id, track.durationMs ?: 0L)
        }
    }

    fun markTrackUnlistened(track: Track) {
        val book = uiState.value.book ?: return
        if (book.contentType != ContentType.AUDIOBOOK) return
        scope.launch {
            persistAudiobookProgress(book.id, track.id, 0L)
        }
    }

    suspend fun persistAudiobookProgress(bookId: String, trackId: String, positionMs: Long) {
        val progress = AudiobookProgress(
            bookId = bookId,
            trackId = trackId,
            positionMs = positionMs,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
        progressSyncRepository.saveLocal(progress, pendingSync = true, session?.accessToken)
        val stored = catalogRepository.getProgress(bookId)
        val book = uiState.value.book
        val status = if (book != null && book.id == bookId) {
            resolveSyncStatus(book, stored ?: progress)
        } else {
            SyncDisplayStatus.PENDING
        }
        uiState.update {
            it.copy(
                audiobookProgress = stored ?: progress,
                syncStatus = status,
            )
        }
    }

    suspend fun resolveSyncStatus(book: Book, progress: AudiobookProgress?): SyncDisplayStatus {
        if (book.contentType != ContentType.AUDIOBOOK) return SyncDisplayStatus.NONE
        if (progress == null) return SyncDisplayStatus.NONE
        return if (catalogRepository.isProgressPendingSync(book.id)) {
            SyncDisplayStatus.PENDING
        } else {
            SyncDisplayStatus.SYNCED
        }
    }
}
