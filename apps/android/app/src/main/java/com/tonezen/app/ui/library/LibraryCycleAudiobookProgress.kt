package com.tonezen.app.ui.library

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.playback.PlaybackSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal suspend fun LibraryCycleHandlerContext.onAudiobookSnapshot(snapshot: PlaybackSnapshot) {
    val audiobookTrackId = snapshot.trackId ?: return
    val bookId = if (
        session.activeAudiobookTrackId == audiobookTrackId &&
        session.activeAudiobookBookId != null
    ) {
        session.activeAudiobookBookId!!
    } else {
        withContext(Dispatchers.IO) {
            catalogRepository.findBookForTrack(audiobookTrackId)?.id
        } ?: return
    }
    session.activeAudiobookBookId = bookId
    session.activeAudiobookTrackId = audiobookTrackId
    val wasPlaying = session.wasAudiobookPlaying
    session.wasAudiobookPlaying = snapshot.isPlaying
    when {
        snapshot.isPlaying -> maybeSaveAudiobookProgress(bookId, audiobookTrackId, snapshot.positionMs)
        wasPlaying -> flushAudiobookProgress(bookId, audiobookTrackId, snapshot.positionMs)
    }
}

internal fun LibraryCycleHandlerContext.flushActiveAudiobookProgress(snapshot: PlaybackSnapshot) {
    if (snapshot.contentType != ContentType.AUDIOBOOK) return
    val trackId = snapshot.trackId ?: session.activeAudiobookTrackId ?: return
    scope.launch {
        val bookId = session.activeAudiobookBookId
            ?: withContext(Dispatchers.IO) {
                catalogRepository.findBookForTrack(trackId)?.id
            }
            ?: return@launch
        flushAudiobookProgress(bookId, trackId, snapshot.positionMs)
    }
}

private fun LibraryCycleHandlerContext.maybeSaveAudiobookProgress(
    bookId: String,
    trackId: String,
    positionMs: Long,
) {
    val now = System.currentTimeMillis()
    if (now - session.lastAudiobookProgressSaveMs < 15_000) return
    session.lastAudiobookProgressSaveMs = now
    scope.launch {
        persistAudiobookProgress(bookId, trackId, positionMs)
        refreshCycleCardStates(cyclesContaining(bookId), uiState.value.downloadedBookIds)
    }
}

private fun LibraryCycleHandlerContext.flushAudiobookProgress(
    bookId: String,
    trackId: String,
    positionMs: Long,
) {
    session.lastAudiobookProgressSaveMs = System.currentTimeMillis()
    scope.launch {
        persistAudiobookProgress(bookId, trackId, positionMs)
        refreshCycleCardStates(cyclesContaining(bookId), uiState.value.downloadedBookIds)
    }
}

internal suspend fun LibraryCycleHandlerContext.persistAudiobookProgress(
    bookId: String,
    trackId: String,
    positionMs: Long,
    allowZero: Boolean = false,
) {
    val existing = withContext(Dispatchers.IO) {
        catalogRepository.getProgress(bookId)
    }
    val effectivePositionMs = when {
        allowZero -> positionMs
        positionMs > 0L -> positionMs
        // Media3 often reports 0 at play-start/pause edges — do not wipe a real head.
        existing?.trackId == trackId && existing.positionMs > 0L -> existing.positionMs
        // New play head must stay distinguishable from «не прослушанным» (explicit 0).
        else -> 1L
    }
    val progress = AudiobookProgress(
        bookId = bookId,
        trackId = trackId,
        positionMs = effectivePositionMs,
        updatedAtEpochMs = System.currentTimeMillis(),
    )
    val storedSession = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
    progressSyncRepository.saveLocal(progress, pendingSync = true, storedSession?.accessToken)
}

internal fun LibraryCycleHandlerContext.cyclesContaining(bookId: String): List<Cycle> =
    uiState.value.cycles.filter { cycle -> cycle.books.any { it.id == bookId } }
