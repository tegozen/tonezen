package com.tonezen.app.ui.library

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.EnqueueDownloadRequest
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.playback.PlaybackCoordinator
import com.tonezen.app.domain.progress.CycleResumeTarget
import com.tonezen.app.domain.progress.isCycleFullyListened
import com.tonezen.app.domain.progress.orderedCycleEntriesFromResume
import com.tonezen.app.domain.progress.resolveCycleContinueState
import com.tonezen.app.domain.progress.resolveCycleListenFraction
import com.tonezen.app.domain.progress.resolveCycleResumeTarget
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.TrackDownloadQueueController
import com.tonezen.app.playback.PlaybackQueueBuilder
import com.tonezen.app.playback.PlaybackSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class LibraryCycleHandler(
    private val uiState: MutableStateFlow<LibraryUiState>,
    private val scope: CoroutineScope,
    private val session: LibraryPlaybackSession,
    private val catalogRepository: CatalogRepository,
    private val downloadRepository: DownloadRepository,
    private val sessionRepository: SessionRepository,
    private val progressSyncRepository: ProgressSyncRepository,
    private val trackDownloadEnsurer: TrackDownloadEnsurer,
    private val downloadQueueController: TrackDownloadQueueController,
    private val downloadQueueNotifier: DownloadQueueNotifier,
    private val playbackClient: PlaybackClient,
    private val playbackQueueBuilder: PlaybackQueueBuilder,
    private val localLibraryNotifier: LocalLibraryNotifier,
    private val playbackErrorMessage: (EnsureTrackOutcome.Failure?) -> String,
) {
    private val playbackCoordinator = PlaybackCoordinator()

    var cycleDownloadBatchId: String? = null
    private var cyclePlayJob: Job? = null

    fun toggleCyclePlay(cycle: Cycle) {
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

    fun downloadCycle(cycle: Cycle) {
        val snapshot = downloadQueueNotifier.snapshot()
        if (snapshot.isBulkDownloading && snapshot.activeBatchId == cycleDownloadBatchId) {
            snapshot.activeBatchId?.let { downloadQueueController.cancelBatch(it) }
            cycleDownloadBatchId = null
            return
        }
        val batchId = java.util.UUID.randomUUID().toString()
        cycleDownloadBatchId = batchId
        scope.launch {
            val bookIds = cycle.bookOrder.mapNotNull { slug ->
                cycle.books.find { it.slug == slug }?.id
            }
            val tracksByBookId = withContext(Dispatchers.IO) {
                catalogRepository.getTracksByBookIds(bookIds)
            }
            val requests = buildList {
                for (bookSlug in cycle.bookOrder) {
                    val book = cycle.books.find { it.slug == bookSlug } ?: continue
                    tracksByBookId[book.id].orEmpty()
                        .filter { it.localPath.isNullOrBlank() }
                        .forEach { track ->
                            add(
                                EnqueueDownloadRequest(
                                    bookId = book.id,
                                    trackId = track.id,
                                    priority = DownloadPriority.BULK,
                                    batchId = batchId,
                                    title = track.title,
                                    subtitle = book.title,
                                    contentType = ContentType.AUDIOBOOK.name.lowercase(),
                                ),
                            )
                        }
                }
            }
            if (requests.isNotEmpty()) {
                downloadQueueController.enqueueBatch(requests, batchId)
            }
        }
    }

    fun removeCycleDownloads(cycle: Cycle) {
        scope.launch {
            val activeBookId = session.activeAudiobookBookId
            if (activeBookId != null && cycle.books.any { it.id == activeBookId }) {
                cyclePlayJob?.cancel()
                playbackClient.stopAndRelease()
                uiState.update { it.copy(cyclePlayback = CyclePlaybackUi()) }
            }
            withContext(Dispatchers.IO) {
                val bookIds = cycle.books.map { it.id }
                val tracksByBookId = catalogRepository.getTracksByBookIds(bookIds)
                for (book in cycle.books) {
                    catalogRepository.clearLocalDownloads(book.id)
                    tracksByBookId[book.id].orEmpty().forEach { track ->
                        downloadRepository.deleteLocalTrack(book.id, track.id)
                    }
                }
            }
            localLibraryNotifier.notifyLocalLibraryChanged()
            refreshDownloadedBooks()
            refreshCycleCardStates(listOf(cycle), uiState.value.downloadedBookIds)
        }
    }

    fun toggleCycleListened(cycle: Cycle) {
        scope.launch {
            val bookIds = cycle.books.map { it.id }
            // Batch queries for tracks and progress to reduce DB round trips
            val (tracksByBookId, progressByBookId) = withContext(Dispatchers.IO) {
                catalogRepository.getTracksByBookIds(bookIds) to
                    catalogRepository.getProgressByBookIds(bookIds)
            }
            if (isCycleFullyListened(cycle, tracksByBookId, progressByBookId)) {
                markCycleUnlistened(cycle)
            } else {
                markCycleListened(cycle)
            }
        }
    }

    fun markCycleListened(cycle: Cycle) {
        scope.launch {
            // Use batched query to avoid multiple DB lookups in loop
            withContext(Dispatchers.IO) {
                val bookIds = cycle.bookOrder.mapNotNull { slug ->
                    cycle.books.find { it.slug == slug }?.id
                }
                val tracksByBookId = catalogRepository.getTracksByBookIds(bookIds.toSet())
                for (bookSlug in cycle.bookOrder) {
                    val book = cycle.books.find { it.slug == bookSlug } ?: continue
                    val tracks = tracksByBookId[book.id].orEmpty().sortedBy { it.sortOrder }
                    val lastTrack = tracks.lastOrNull() ?: continue
                    persistAudiobookProgress(book.id, lastTrack.id, lastTrack.durationMs ?: 0L)
                }
            }
            refreshCycleCardStates(listOf(cycle), uiState.value.downloadedBookIds)
        }
    }

    fun markCycleUnlistened(cycle: Cycle) {
        scope.launch {
            withContext(Dispatchers.IO) {
                cycle.books.forEach { book ->
                    catalogRepository.clearProgress(book.id)
                }
            }
            refreshCycleCardStates(listOf(cycle), uiState.value.downloadedBookIds)
        }
    }

    fun refreshCycleMenu(cycle: Cycle) {
        scope.launch {
            refreshCycleCardStates(listOf(cycle), uiState.value.downloadedBookIds)
        }
    }

    suspend fun refreshCycleCardStates(cycles: List<Cycle>, downloadedBookIds: Set<String>) {
        val refreshData = withContext(Dispatchers.IO) {
            val bookIds = cycles.flatMap { cycle -> cycle.books.map { it.id } }.toSet()
            // Use batched queries instead of individual lookups to reduce DB round trips
            val tracksByBookId = catalogRepository.getTracksByBookIds(bookIds)
            val progressByBookId = catalogRepository.getProgressByBookIds(bookIds)
            val cardStates = cycles.associate { cycle ->
                val cycleTracks = cycle.books.associate { book ->
                    book.id to tracksByBookId[book.id].orEmpty()
                }
                val cycleProgress = cycle.books.associate { book ->
                    book.id to progressByBookId[book.id]
                }
                val fraction = resolveCycleListenFraction(cycle, cycleTracks, cycleProgress)
                val allTracks = cycleTracks.values.flatten()
                val isFullyDownloaded = allTracks.isNotEmpty() &&
                    allTracks.all { !it.localPath.isNullOrBlank() }
                cycle.id to CycleCardState(
                    isDownloaded = isFullyDownloaded,
                    progressFraction = fraction?.takeIf { it > 0f },
                    continueState = resolveCycleContinueState(cycle, cycleTracks, cycleProgress),
                    showDownload = allTracks.any { it.localPath.isNullOrBlank() },
                    showRemoveDownload = allTracks.any { !it.localPath.isNullOrBlank() },
                    isListened = isCycleFullyListened(cycle, cycleTracks, cycleProgress),
                )
            }
            RefreshCycleCardData(
                cardStates = cardStates,
                progressTimestamps = progressByBookId.mapValues { entry -> entry.value?.updatedAtEpochMs ?: 0L },
                tracksByBookId = tracksByBookId,
                progressByBookId = progressByBookId,
            )
        }
        uiState.update {
            it.copy(
                cycleCardStateById = refreshData.cardStates,
                tracksByBookId = it.tracksByBookId + refreshData.tracksByBookId,
                audiobookProgressByBookId = it.audiobookProgressByBookId + refreshData.progressByBookId,
                progressUpdatedAtByBookId = it.progressUpdatedAtByBookId + refreshData.progressTimestamps,
            )
        }
    }

    fun resolveCyclePlaybackUi(snapshot: PlaybackSnapshot): CyclePlaybackUi {
        if (snapshot.contentType != ContentType.AUDIOBOOK || session.activeAudiobookBookId == null) {
            return if (uiState.value.cyclePlayback.isPreparing) {
                uiState.value.cyclePlayback
            } else {
                CyclePlaybackUi()
            }
        }
        val cycleId = uiState.value.cycles.firstOrNull { cycle ->
            cycle.books.any { it.id == session.activeAudiobookBookId }
        }?.id ?: return CyclePlaybackUi()
        val preparing = uiState.value.cyclePlayback.isPreparing &&
            uiState.value.cyclePlayback.cycleId == cycleId &&
            !snapshot.isPlaying
        return CyclePlaybackUi(
            cycleId = cycleId,
            isPlaying = snapshot.isPlaying,
            isPreparing = preparing,
            downloadProgress = if (preparing) {
                uiState.value.cyclePlayback.downloadProgress
            } else {
                null
            },
        )
    }

    suspend fun onAudiobookSnapshot(snapshot: PlaybackSnapshot) {
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

    fun flushActiveAudiobookProgress(snapshot: PlaybackSnapshot) {
        if (snapshot.contentType != ContentType.AUDIOBOOK || !snapshot.isPlaying) return
        val trackId = snapshot.trackId ?: return
        scope.launch {
            val bookId = withContext(Dispatchers.IO) {
                catalogRepository.findBookForTrack(trackId)?.id
            } ?: return@launch
            flushAudiobookProgress(bookId, trackId, snapshot.positionMs)
        }
    }

    fun handleAudiobookTrackEnded() {
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

    private fun maybeSaveAudiobookProgress(bookId: String, trackId: String, positionMs: Long) {
        val now = System.currentTimeMillis()
        if (now - session.lastAudiobookProgressSaveMs < 15_000) return
        session.lastAudiobookProgressSaveMs = now
        scope.launch {
            persistAudiobookProgress(bookId, trackId, positionMs)
            refreshCycleCardStates(cyclesContaining(bookId), uiState.value.downloadedBookIds)
        }
    }

    private fun flushAudiobookProgress(bookId: String, trackId: String, positionMs: Long) {
        session.lastAudiobookProgressSaveMs = System.currentTimeMillis()
        scope.launch {
            persistAudiobookProgress(bookId, trackId, positionMs)
            refreshCycleCardStates(cyclesContaining(bookId), uiState.value.downloadedBookIds)
        }
    }

    private suspend fun persistAudiobookProgress(bookId: String, trackId: String, positionMs: Long) {
        val progress = AudiobookProgress(
            bookId = bookId,
            trackId = trackId,
            positionMs = positionMs,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        val storedSession = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
        progressSyncRepository.saveLocal(progress, pendingSync = true, storedSession?.accessToken)
    }

    private suspend fun playCycleInternal(cycle: Cycle) {
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

    private fun cyclesContaining(bookId: String): List<Cycle> =
        uiState.value.cycles.filter { cycle -> cycle.books.any { it.id == bookId } }

    private fun stopAudiobookAdvance(message: String) {
        playbackClient.stopAndRelease()
        uiState.update {
            it.copy(
                cyclePlaybackErrorMessage = message,
                cyclePlayback = CyclePlaybackUi(),
            )
        }
    }

    private fun playbackErrorMessageForAwait(result: DownloadAwaitResult): String = when (result) {
        DownloadAwaitResult.OFFLINE -> playbackErrorMessage(EnsureTrackOutcome.Failure.OFFLINE)
        DownloadAwaitResult.FAILED -> playbackErrorMessage(EnsureTrackOutcome.Failure.DOWNLOAD_FAILED)
        DownloadAwaitResult.CANCELLED, DownloadAwaitResult.COMPLETED ->
            playbackErrorMessage(EnsureTrackOutcome.Failure.DOWNLOAD_FAILED)
    }

    private fun prefetchNextCycleChapter(
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

    private suspend fun refreshDownloadedBooks() {
        val downloaded = withContext(Dispatchers.IO) {
            catalogRepository.downloadedBookIds(uiState.value.books)
        }
        uiState.update { it.copy(downloadedBookIds = downloaded) }
    }
}

private data class RefreshCycleCardData(
    val cardStates: Map<String, CycleCardState>,
    val progressTimestamps: Map<String, Long>,
    val tracksByBookId: Map<String, List<Track>>,
    val progressByBookId: Map<String, AudiobookProgress?>,
)
