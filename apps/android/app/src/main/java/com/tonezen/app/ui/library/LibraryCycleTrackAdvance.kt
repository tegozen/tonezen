package com.tonezen.app.ui.library

import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.progress.CycleResumeTarget
import com.tonezen.app.domain.progress.orderedCycleEntriesFromResume
import com.tonezen.app.domain.progress.resolveCycleResumeTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun LibraryCycleHandlerContext.handleAudiobookTrackEnded() {
    val bookId = session.activeAudiobookBookId ?: return
    val trackId = session.activeAudiobookTrackId ?: return
    scope.launch {
        val book = uiState.value.books.find { it.id == bookId } ?: return@launch
        val track = withContext(Dispatchers.IO) {
            catalogRepository.getTracksForBook(bookId).find { it.id == trackId }
        } ?: return@launch
        persistAudiobookProgress(bookId, trackId, track.durationMs ?: 0L)
        refreshCycleCardStates(cyclesContaining(bookId), uiState.value.downloadedBookIds)

        val cycle = uiState.value.cycles.find { item ->
            item.books.any { it.id == bookId }
        }
        val tracksByBookId = withContext(Dispatchers.IO) {
            if (cycle != null) {
                catalogRepository.getTracksByBookIds(cycle.books.map { it.id })
            } else {
                mapOf(bookId to catalogRepository.getTracksForBook(bookId))
            }
        }
        val booksBySlug = cycle?.books?.associateBy { it.slug } ?: emptyMap()
        val nextTarget = playbackCoordinator.resolveAutoAdvance(
            currentBook = book,
            currentTrack = track,
            cycle = cycle,
            booksBySlug = booksBySlug,
            tracksByBookId = tracksByBookId,
        )
        val nextBook = nextTarget.book ?: return@launch
        val nextTrack = nextTarget.track ?: return@launch

        val needsNextDownload = withContext(Dispatchers.IO) {
            !trackDownloadEnsurer.isTrackLocal(nextBook.id, nextTrack.id)
        }
        val localTrack = if (needsNextDownload) {
            val awaitResult = downloadQueueController.awaitTrack(
                bookId = nextBook.id,
                trackId = nextTrack.id,
                priority = DownloadPriority.PLAY,
                title = nextTrack.title,
                subtitle = nextBook.title,
                contentType = ContentType.AUDIOBOOK.name.lowercase(),
            )
            if (awaitResult != DownloadAwaitResult.COMPLETED) {
                stopAudiobookAdvance(playbackErrorMessageForAwait(awaitResult))
                return@launch
            }
            withContext(Dispatchers.IO) {
                trackDownloadEnsurer.resolveLocalTrack(nextBook.id, nextTrack)
            }
        } else {
            withContext(Dispatchers.IO) {
                trackDownloadEnsurer.resolveLocalTrack(nextBook.id, nextTrack)
            }
        }
        if (localTrack == null) {
            stopAudiobookAdvance(playbackErrorMessage(EnsureTrackOutcome.Failure.DOWNLOAD_FAILED))
            return@launch
        }

        if (nextTarget.isNextBookInCycle && cycle != null) {
            val progressByBookId = withContext(Dispatchers.IO) {
                catalogRepository.getProgressByBookIds(cycle.books.map { it.id })
            }
            val resume = resolveCycleResumeTarget(cycle, tracksByBookId, progressByBookId)
                ?.takeIf { it.book.id == nextBook.id && it.track.id == nextTrack.id }
                ?: CycleResumeTarget(
                    book = nextBook,
                    track = localTrack,
                    startPositionMs = 0L,
                )
            val entries = orderedCycleEntriesFromResume(cycle, tracksByBookId, resume)
            val queueResult = withContext(Dispatchers.IO) {
                playbackQueueBuilder.buildCycleQueue(entries, localTrack)
            } ?: return@launch
            session.activeAudiobookBookId = nextBook.id
            session.activeAudiobookTrackId = localTrack.id
            uiState.update {
                it.copy(
                    nowPlayingTitle = localTrack.title,
                    cyclePlaybackErrorMessage = null,
                    cyclePlayback = CyclePlaybackUi(cycleId = cycle.id, isPlaying = true),
                )
            }
            playbackClient.playQueue(queueResult.items, queueResult.startIndex, resume.startPositionMs)
        } else {
            val refreshedTracks = withContext(Dispatchers.IO) {
                catalogRepository.getTracksForBook(nextBook.id).sortedBy { it.sortOrder }
            }
            val queue = playbackQueueBuilder.buildQueueFromLocalTracks(nextBook, refreshedTracks)
            if (queue.isEmpty()) return@launch
            val startIndex = queue.indexOfFirst { it.trackId == localTrack.id }.coerceAtLeast(0)
            session.activeAudiobookBookId = nextBook.id
            session.activeAudiobookTrackId = localTrack.id
            uiState.update {
                it.copy(
                    nowPlayingTitle = localTrack.title,
                    cyclePlaybackErrorMessage = null,
                )
            }
            playbackClient.playQueue(queue, startIndex)
        }
        localLibraryNotifier.notifyLocalLibraryChanged()
        refreshDownloadedBooks()
        if (cycle != null) {
            refreshCycleCardStates(listOf(cycle), uiState.value.downloadedBookIds)
        }
    }
}

private fun LibraryCycleHandlerContext.stopAudiobookAdvance(message: String) {
    playbackClient.stopAndRelease()
    uiState.update {
        it.copy(
            cyclePlaybackErrorMessage = message,
            cyclePlayback = CyclePlaybackUi(),
        )
    }
}
