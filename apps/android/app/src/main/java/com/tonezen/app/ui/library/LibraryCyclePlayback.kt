package com.tonezen.app.ui.library

import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.EnqueueDownloadRequest
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.progress.CycleResumeTarget
import com.tonezen.app.domain.progress.ProgressMerger
import com.tonezen.app.domain.progress.orderedCycleEntriesFromResume
import com.tonezen.app.ui.components.formatPlaybackProgressLabel
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
    cyclePlayJob = scope.launch { playCycleInternal(cycle, skipSyncConflictPrompt = false) }
}

internal fun LibraryCycleHandlerContext.dismissCycleProgressSyncConflict() {
    uiState.update {
        it.copy(
            confirmProgressSyncConflict = null,
            cyclePlayback = CyclePlaybackUi(),
        )
    }
}

internal fun LibraryCycleHandlerContext.chooseCycleProgressSyncLocal() {
    val prompt = uiState.value.confirmProgressSyncConflict ?: return
    val cycle = uiState.value.cycles.find { it.id == prompt.cycleId } ?: return
    uiState.update { it.copy(confirmProgressSyncConflict = null) }
    cyclePlayJob?.cancel()
    cyclePlayJob = scope.launch {
        withContext(Dispatchers.IO) {
            val sessionData = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
            progressSyncRepository.chooseLocalProgress(prompt.bookId, sessionData?.accessToken)
        }
        playCycleInternal(cycle, skipSyncConflictPrompt = true)
    }
}

internal fun LibraryCycleHandlerContext.chooseCycleProgressSyncServer() {
    val prompt = uiState.value.confirmProgressSyncConflict ?: return
    val cycle = uiState.value.cycles.find { it.id == prompt.cycleId } ?: return
    uiState.update { it.copy(confirmProgressSyncConflict = null) }
    cyclePlayJob?.cancel()
    cyclePlayJob = scope.launch {
        withContext(Dispatchers.IO) {
            progressSyncRepository.chooseServerProgress(prompt.bookId)
        }
        playCycleInternal(cycle, skipSyncConflictPrompt = true)
    }
}

internal suspend fun LibraryCycleHandlerContext.playCycleInternal(
    cycle: Cycle,
    skipSyncConflictPrompt: Boolean,
) {
    uiState.update {
        it.copy(
            cyclePlaybackErrorMessage = null,
            confirmProgressSyncConflict = null,
            cyclePlayback = CyclePlaybackUi(
                cycleId = cycle.id,
                isPreparing = true,
                downloadProgress = 0f,
            ),
        )
    }
    val source = cyclePlaybackLoader.load(cycle)
    val tracksByBookId = source.tracksByBookId
    val progressByBookId = source.progressByBookId
    var resume = source.resume
    if (resume == null) {
        uiState.update {
            it.copy(
                cyclePlayback = CyclePlaybackUi(),
                cyclePlaybackErrorMessage = "В цикле нет доступных глав для воспроизведения",
            )
        }
        return
    }

    val resumeProgress = progressByBookId[resume.book.id]
    if (!skipSyncConflictPrompt && ProgressMerger.shouldPrompt(resumeProgress)) {
        val snapshot = ProgressMerger.getServerSnapshot(resumeProgress)!!
        val bookTracks = tracksByBookId[resume.book.id].orEmpty()
        uiState.update {
            it.copy(
                cyclePlayback = CyclePlaybackUi(),
                confirmProgressSyncConflict = CycleProgressSyncConflictPrompt(
                    cycleId = cycle.id,
                    bookId = resume.book.id,
                    localLabel = formatPlaybackProgressLabel(
                        bookTracks,
                        resumeProgress!!.trackId,
                        resumeProgress.positionMs,
                    ),
                    serverLabel = formatPlaybackProgressLabel(
                        bookTracks,
                        snapshot.trackId,
                        snapshot.positionMs,
                    ),
                ),
            )
        }
        return
    }

    // After A3b choice, play head may have changed — re-resolve against refreshed progress.
    if (skipSyncConflictPrompt) {
        resume = cyclePlaybackLoader.refreshResume(cycle, source) ?: resume
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
    // Persist play head immediately (same as book detail) so Continue does not wait for 15s throttle.
    withContext(Dispatchers.IO) {
        persistAudiobookProgress(resume.book.id, resume.track.id, resume.startPositionMs)
    }
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
