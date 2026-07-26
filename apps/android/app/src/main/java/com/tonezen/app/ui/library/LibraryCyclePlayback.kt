package com.tonezen.app.ui.library

import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.EnqueueDownloadRequest
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.progress.CycleResumeTarget
import com.tonezen.app.domain.progress.orderedCycleEntriesFromResume
import com.tonezen.app.domain.progress.resolveCycleResumeTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun LibraryCycleHandlerContext.toggleCyclePlay(cycle: Cycle) {
    val cyclePlayback = uiState.value.cyclePlayback
    if (cyclePlayback.isPreparing && cyclePlayback.cycleId == cycle.id) return

    val activeCycleId = session.activeAudiobookBookId?.let { bookId ->
        uiState.value.cycles.firstOrNull { item ->
            item.books.any { it.id == bookId }
        }?.id
    }
    val snapshot = playbackClient.snapshot.value
    if (
        activeCycleId == cycle.id &&
        snapshot.trackId != null &&
        snapshot.contentType == ContentType.AUDIOBOOK
    ) {
        if (snapshot.isPlaying) {
            playbackClient.pause()
        } else {
            playbackClient.play()
        }
        return
    }
    cyclePlayJob?.cancel()
    playbackClient.stopAndRelease()
    cyclePlayJob = scope.launch { playCycleInternal(cycle) }
}

internal suspend fun LibraryCycleHandlerContext.playCycleInternal(cycle: Cycle) {
    uiState.update {
        it.copy(
            cyclePlaybackErrorMessage = null,
            cyclePlayback = CyclePlaybackUi(
                cycleId = cycle.id,
                isPreparing = true,
                downloadProgress = 0f,
            ),
        )
    }
    val bookIds = cycle.books.map { it.id }
    val (tracksByBookId, progressByBookId) = withContext(Dispatchers.IO) {
        catalogRepository.getTracksByBookIds(bookIds) to
            catalogRepository.getProgressByBookIds(bookIds)
    }
    val resume = resolveCycleResumeTarget(cycle, tracksByBookId, progressByBookId)
    if (resume == null) {
        uiState.update {
            it.copy(
                cyclePlayback = CyclePlaybackUi(),
                cyclePlaybackErrorMessage = "В цикле нет доступных глав для воспроизведения",
            )
        }
        return
    }
    val entries = orderedCycleEntriesFromResume(cycle, tracksByBookId, resume)
    if (entries.isEmpty()) {
        uiState.update {
            it.copy(
                cyclePlayback = CyclePlaybackUi(),
                cyclePlaybackErrorMessage = "В цикле нет доступных глав для воспроизведения",
            )
        }
        return
    }
    val needsDownload = withContext(Dispatchers.IO) {
        !trackDownloadEnsurer.isTrackLocal(resume.book.id, resume.track.id)
    }
    val startOutcome = if (needsDownload) {
        val awaitResult = downloadQueueController.awaitTrack(
            bookId = resume.book.id,
            trackId = resume.track.id,
            priority = DownloadPriority.PLAY,
            title = resume.track.title,
            subtitle = resume.book.title,
            contentType = ContentType.AUDIOBOOK.name.lowercase(),
        )
        if (awaitResult != DownloadAwaitResult.COMPLETED) {
            uiState.update {
                it.copy(
                    cyclePlayback = CyclePlaybackUi(),
                    cyclePlaybackErrorMessage = playbackErrorMessageForAwait(awaitResult),
                )
            }
            return
        }
        withContext(Dispatchers.IO) {
            trackDownloadEnsurer.resolveLocalTrack(resume.book.id, resume.track)
        }?.let { EnsureTrackOutcome.success(it) }
            ?: EnsureTrackOutcome.failed(EnsureTrackOutcome.Failure.DOWNLOAD_FAILED)
    } else {
        withContext(Dispatchers.IO) {
            trackDownloadEnsurer.resolveLocalTrack(resume.book.id, resume.track)
        }?.let { EnsureTrackOutcome.success(it) }
            ?: EnsureTrackOutcome.failed(EnsureTrackOutcome.Failure.DOWNLOAD_FAILED)
    }
    val startTrack = startOutcome.track
    if (startTrack == null) {
        uiState.update {
            it.copy(
                cyclePlayback = CyclePlaybackUi(),
                cyclePlaybackErrorMessage = playbackErrorMessage(startOutcome.failure),
            )
        }
        return
    }
    val queueResult = withContext(Dispatchers.IO) {
        playbackQueueBuilder.buildCycleQueue(entries, startTrack)
    }
    if (queueResult == null || queueResult.items.isEmpty()) {
        uiState.update {
            it.copy(
                cyclePlayback = CyclePlaybackUi(),
                cyclePlaybackErrorMessage = "В цикле нет доступных глав для воспроизведения",
            )
        }
        return
    }
    session.activeAudiobookBookId = resume.book.id
    session.activeAudiobookTrackId = resume.track.id
    uiState.update {
        it.copy(
            nowPlayingTitle = startTrack.title,
            cyclePlaybackErrorMessage = null,
            cyclePlayback = CyclePlaybackUi(cycleId = cycle.id),
        )
    }
    playbackClient.playQueue(
        queueResult.items,
        queueResult.startIndex,
        resume.startPositionMs,
    )
    prefetchNextCycleChapter(cycle, tracksByBookId, resume)
    refreshDownloadedBooks()
    refreshCycleCardStates(listOf(cycle), uiState.value.downloadedBookIds)
}

internal fun LibraryCycleHandlerContext.playbackErrorMessageForAwait(result: DownloadAwaitResult): String =
    when (result) {
        DownloadAwaitResult.OFFLINE -> playbackErrorMessage(EnsureTrackOutcome.Failure.OFFLINE)
        DownloadAwaitResult.FAILED -> playbackErrorMessage(EnsureTrackOutcome.Failure.DOWNLOAD_FAILED)
        DownloadAwaitResult.CANCELLED, DownloadAwaitResult.COMPLETED ->
            playbackErrorMessage(EnsureTrackOutcome.Failure.DOWNLOAD_FAILED)
    }

private fun LibraryCycleHandlerContext.prefetchNextCycleChapter(
    cycle: Cycle,
    tracksByBookId: Map<String, List<Track>>,
    resume: CycleResumeTarget,
) {
    if (!uiState.value.isNetworkOnline) return
    val entries = orderedCycleEntriesFromResume(cycle, tracksByBookId, resume)
    if (entries.size < 2) return
    val next = entries[1]
    if (!next.second.localPath.isNullOrBlank()) return
    downloadQueueController.enqueue(
        EnqueueDownloadRequest(
            bookId = next.first.id,
            trackId = next.second.id,
            priority = DownloadPriority.PREFETCH,
            title = next.second.title,
            subtitle = next.first.title,
            contentType = ContentType.AUDIOBOOK.name.lowercase(),
        ),
    )
}
