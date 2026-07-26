package com.tonezen.app.ui.library

import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.EnqueueDownloadRequest
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal fun LibraryCycleHandlerContext.downloadCycle(cycle: Cycle) {
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

internal fun LibraryCycleHandlerContext.removeCycleDownloads(cycle: Cycle) {
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

internal suspend fun LibraryCycleHandlerContext.refreshDownloadedBooks() {
    val downloaded = withContext(Dispatchers.IO) {
        catalogRepository.downloadedBookIds(uiState.value.books)
    }
    uiState.update { it.copy(downloadedBookIds = downloaded) }
}
