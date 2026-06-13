package com.tonezen.app.ui.library

import com.tonezen.app.R
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.progress.isCycleFullyListened
import com.tonezen.app.domain.progress.orderedCycleEntriesFromResume
import com.tonezen.app.domain.progress.resolveCycleListenFraction
import com.tonezen.app.domain.progress.resolveCycleResumeTarget
import com.tonezen.app.playback.PlaybackClient
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
    private val playbackClient: PlaybackClient,
    private val playbackQueueBuilder: PlaybackQueueBuilder,
    private val localLibraryNotifier: LocalLibraryNotifier,
    private val musicDownloadActive: () -> Boolean,
    private val cancelPlayJob: () -> Unit,
    private val playbackErrorRes: (EnsureTrackOutcome.Failure?) -> Int,
) {
    var cycleDownloadJob: Job? = null

    fun toggleCyclePlay(cycle: Cycle, playJobSetter: (Job?) -> Unit) {
        if (musicDownloadActive()) return
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
        cancelPlayJob()
        playJobSetter(scope.launch { playCycleInternal(cycle) })
    }

    fun downloadCycle(cycle: Cycle) {
        if (cycleDownloadJob?.isActive == true || musicDownloadActive()) return
        cycleDownloadJob = scope.launch {
            val sessionData = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
                ?: return@launch
            val accessToken = sessionData.accessToken
            withContext(Dispatchers.IO) {
                val bookIds = cycle.bookOrder.mapNotNull { slug ->
                    cycle.books.find { it.slug == slug }?.id
                }
                val tracksByBookId = catalogRepository.getTracksByBookIds(bookIds)
                for (bookSlug in cycle.bookOrder) {
                    val book = cycle.books.find { it.slug == bookSlug } ?: continue
                    tracksByBookId[book.id].orEmpty()
                        .filter { it.localPath.isNullOrBlank() }
                        .forEach { track ->
                            val file = downloadRepository.downloadTrack(
                                accessToken,
                                book.id,
                                track.id,
                            ) { }
                            catalogRepository.markTrackDownloaded(book.id, track.id, file.absolutePath)
                        }
                }
            }
            localLibraryNotifier.notifyLocalLibraryChanged()
            refreshDownloadedBooks()
            refreshCycleCardStates(listOf(cycle), uiState.value.downloadedBookIds)
        }
    }

    fun removeCycleDownloads(cycle: Cycle) {
        if (cycleDownloadJob?.isActive == true) return
        scope.launch {
            val activeBookId = session.activeAudiobookBookId
            if (activeBookId != null && cycle.books.any { it.id == activeBookId }) {
                cancelPlayJob()
                playbackClient.stopAndRelease()
                uiState.update { it.copy(cyclePlayback = CyclePlaybackUi()) }
            }
            withContext(Dispatchers.IO) {
                val tracksByBookId = catalogRepository.getTracksByBookIds(cycle.books.map { it.id })
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
            withContext(Dispatchers.IO) {
                val tracksByBookId = catalogRepository.getTracksByBookIds(cycle.books.map { it.id })
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
        val states = withContext(Dispatchers.IO) {
            val bookIds = cycles.flatMap { cycle -> cycle.books.map { it.id } }.toSet()
            val tracksByBookId = catalogRepository.getTracksByBookIds(bookIds)
            val progressByBookId = catalogRepository.getProgressByBookIds(bookIds)
            cycles.associate { cycle ->
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
                    showDownload = allTracks.any { it.localPath.isNullOrBlank() },
                    showRemoveDownload = allTracks.any { !it.localPath.isNullOrBlank() },
                    isListened = isCycleFullyListened(cycle, cycleTracks, cycleProgress),
                )
            }
        }
        uiState.update { it.copy(cycleCardStateById = states) }
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
        val bookId = withContext(Dispatchers.IO) {
            catalogRepository.findBookForTrack(audiobookTrackId)?.id
        } ?: return
        session.activeAudiobookBookId = bookId
        session.activeAudiobookTrackId = audiobookTrackId
        if (snapshot.isPlaying) {
            maybeSaveAudiobookProgress(bookId, audiobookTrackId, snapshot.positionMs)
        }
    }

    fun handleAudiobookTrackEnded() {
        val bookId = session.activeAudiobookBookId ?: return
        val trackId = session.activeAudiobookTrackId ?: return
        scope.launch {
            val track = withContext(Dispatchers.IO) {
                catalogRepository.getTracksForBook(bookId).find { it.id == trackId }
            } ?: return@launch
            persistAudiobookProgress(bookId, trackId, track.durationMs ?: 0L)
            refreshCycleCardStates(cyclesContaining(bookId), uiState.value.downloadedBookIds)
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
                cyclePlaybackErrorRes = null,
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
                    cyclePlaybackErrorRes = R.string.cycle_playback_error_empty,
                )
            }
            return
        }
        val entries = orderedCycleEntriesFromResume(cycle, tracksByBookId, resume)
        if (entries.isEmpty()) {
            uiState.update {
                it.copy(
                    cyclePlayback = CyclePlaybackUi(),
                    cyclePlaybackErrorRes = R.string.cycle_playback_error_empty,
                )
            }
            return
        }
        val needsDownload = withContext(Dispatchers.IO) {
            !trackDownloadEnsurer.isTrackLocal(resume.book.id, resume.track.id)
        }
        val progressReporter = if (needsDownload) {
            createCycleDownloadProgressReporter(cycle.id)
        } else {
            null
        }
        val startOutcome = withContext(Dispatchers.IO) {
            trackDownloadEnsurer.ensureTrackLocal(resume.book.id, resume.track, progressReporter)
        }
        val startTrack = startOutcome.track
        if (startTrack == null) {
            uiState.update {
                it.copy(
                    cyclePlayback = CyclePlaybackUi(),
                    cyclePlaybackErrorRes = playbackErrorRes(startOutcome.failure),
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
                    cyclePlaybackErrorRes = R.string.cycle_playback_error_empty,
                )
            }
            return
        }
        session.activeAudiobookBookId = resume.book.id
        session.activeAudiobookTrackId = resume.track.id
        uiState.update {
            it.copy(
                nowPlayingTitle = startTrack.title,
                cyclePlaybackErrorRes = null,
                cyclePlayback = CyclePlaybackUi(cycleId = cycle.id),
            )
        }
        playbackClient.playQueue(
            queueResult.items,
            queueResult.startIndex,
            resume.startPositionMs,
        )
        refreshDownloadedBooks()
        refreshCycleCardStates(listOf(cycle), uiState.value.downloadedBookIds)
    }

    private fun createCycleDownloadProgressReporter(cycleId: String): (Float) -> Unit {
        val reporter = object {
            var lastBucket = -1
        }
        return progress@{ progress ->
            val bucket = (progress * 50).toInt()
            if (bucket > reporter.lastBucket || progress >= 1f) {
                reporter.lastBucket = bucket
                scope.launch(Dispatchers.Main.immediate) {
                    uiState.update { state ->
                        if (state.cyclePlayback.cycleId != cycleId) return@update state
                        state.copy(
                            cyclePlayback = state.cyclePlayback.copy(downloadProgress = progress),
                        )
                    }
                }
            }
        }
    }

    private fun cyclesContaining(bookId: String): List<Cycle> =
        uiState.value.cycles.filter { cycle -> cycle.books.any { it.id == bookId } }

    private suspend fun refreshDownloadedBooks() {
        val downloaded = withContext(Dispatchers.IO) {
            catalogRepository.downloadedBookIds(uiState.value.books)
        }
        uiState.update { it.copy(downloadedBookIds = downloaded) }
    }
}
