package com.tonezen.app.ui.library

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.progress.isCycleFullyListened
import com.tonezen.app.domain.progress.resolveCycleContinueState
import com.tonezen.app.domain.progress.resolveCycleListenFraction
import com.tonezen.app.playback.PlaybackSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal data class RefreshCycleCardData(
    val cardStates: Map<String, CycleCardState>,
    val progressTimestamps: Map<String, Long>,
    val tracksByBookId: Map<String, List<Track>>,
    val progressByBookId: Map<String, AudiobookProgress?>,
)

internal fun LibraryCycleHandlerContext.toggleCycleListened(cycle: Cycle) {
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

internal fun LibraryCycleHandlerContext.markCycleListened(cycle: Cycle) {
    scope.launch {
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

internal fun LibraryCycleHandlerContext.markCycleUnlistened(cycle: Cycle) {
    scope.launch {
        withContext(Dispatchers.IO) {
            val bookIds = cycle.books.map { it.id }
            val tracksByBookId = catalogRepository.getTracksByBookIds(bookIds)
            for (book in cycle.books) {
                val firstTrack = tracksByBookId[book.id].orEmpty().sortedBy { it.sortOrder }.firstOrNull()
                    ?: continue
                // Reset via synced play head (pos 0) — local delete alone is restored by pull.
                persistAudiobookProgress(book.id, firstTrack.id, 0L)
            }
        }
        refreshCycleCardStates(listOf(cycle), uiState.value.downloadedBookIds)
    }
}

internal fun LibraryCycleHandlerContext.refreshCycleMenu(cycle: Cycle) {
    scope.launch {
        refreshCycleCardStates(listOf(cycle), uiState.value.downloadedBookIds)
    }
}

internal suspend fun LibraryCycleHandlerContext.refreshCycleCardStates(
    cycles: List<Cycle>,
    downloadedBookIds: Set<String>,
) {
    val refreshData = withContext(Dispatchers.IO) {
        val bookIds = cycles.flatMap { cycle -> cycle.books.map { it.id } }.toSet()
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
        val refreshedBookIds = refreshData.tracksByBookId.keys
        val isPartialRefresh = cycles.size < it.cycles.size
        val nextTracks = if (isPartialRefresh) {
            it.tracksByBookId.filterKeys { bookId -> bookId !in refreshedBookIds } + refreshData.tracksByBookId
        } else {
            refreshData.tracksByBookId
        }
        val nextProgress = if (isPartialRefresh) {
            it.audiobookProgressByBookId.filterKeys { bookId -> bookId !in refreshedBookIds } +
                refreshData.progressByBookId
        } else {
            refreshData.progressByBookId
        }
        val nextTimestamps = if (isPartialRefresh) {
            it.progressUpdatedAtByBookId.filterKeys { bookId -> bookId !in refreshedBookIds } +
                refreshData.progressTimestamps
        } else {
            refreshData.progressTimestamps
        }
        val nextCards = if (isPartialRefresh) {
            it.cycleCardStateById + refreshData.cardStates
        } else {
            refreshData.cardStates
        }
        it.copy(
            cycleCardStateById = nextCards,
            tracksByBookId = nextTracks,
            audiobookProgressByBookId = nextProgress,
            progressUpdatedAtByBookId = nextTimestamps,
        )
    }
}

internal fun LibraryCycleHandlerContext.resolveCyclePlaybackUi(snapshot: PlaybackSnapshot): CyclePlaybackUi {
    if (snapshot.contentType != ContentType.AUDIOBOOK ||
        session.activeAudiobookBookId == null
    ) {
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
