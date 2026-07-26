package com.tonezen.app.playback

import android.content.Intent
import com.tonezen.app.data.local.DownloadQueueEntity
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.DownloadQueueKey
import com.tonezen.app.domain.downloads.DownloadQueuePolicy
import com.tonezen.app.domain.downloads.DownloadQueueSortable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class TrackDownloadQueueNotify(
    private val shared: TrackDownloadQueueShared,
) {
    suspend fun refreshNotifierFromDb(paused: Boolean = shared.pausedForNetwork) {
        val rows = shared.downloadQueueRepository.getAll()
        val active = shared.notifier.snapshot()
        val entityByKey = rows.associateBy { DownloadQueueKey(it.bookId, it.trackId) }
        val items = rows.map { entity ->
            val isActive = !paused &&
                entity.bookId == active.activeBookId &&
                entity.trackId == active.activeTrackId
            DownloadQueueItem(
                bookId = entity.bookId,
                trackId = entity.trackId,
                title = entity.title,
                subtitle = entity.subtitle,
                contentType = entity.contentType,
                status = when {
                    paused -> DownloadQueueItemStatus.PAUSED_OFFLINE
                    isActive -> DownloadQueueItemStatus.DOWNLOADING
                    else -> DownloadQueueItemStatus.QUEUED
                },
                progress = if (isActive) active.activeProgress else null,
                batchId = entity.batchId,
                enqueuedAt = entity.enqueuedAt,
                completedAt = null,
            )
        }
        val itemsByKey = items.associateBy { DownloadQueueKey(it.bookId, it.trackId) }
        val sortables = items.mapNotNull { item ->
            val entity = entityByKey[DownloadQueueKey(item.bookId, item.trackId)] ?: return@mapNotNull null
            DownloadQueueSortable(
                key = DownloadQueueKey(item.bookId, item.trackId),
                priority = DownloadPriority.valueOf(entity.priority),
                enqueuedAt = item.enqueuedAt,
            )
        }
        val queued = DownloadQueuePolicy.sortPending(sortables).mapNotNull { sortable ->
            itemsByKey[sortable.key]
        }
        val bulkBatch = shared.bulkBatchId
        val completedInBatch = if (bulkBatch != null) {
            active.completedHistory.count { it.batchId == bulkBatch }
        } else {
            0
        }
        val bulkDone = DownloadQueuePolicy.computeBulkDownloaded(shared.bulkSkipped, bulkBatch, completedInBatch)
        maybeFinishBulkBatchLocked(bulkDone)
        val activeBatch = shared.bulkBatchId
        val activeStillQueued = active.activeTrackId != null && rows.any {
            it.bookId == active.activeBookId && it.trackId == active.activeTrackId
        }
        val clearActive = rows.isEmpty() || !activeStillQueued
        shared.notifier.update { state ->
            state.copy(
                queuedItems = queued,
                activeBookId = if (paused || clearActive) null else state.activeBookId,
                activeTrackId = if (paused || clearActive) null else state.activeTrackId,
                activeProgress = if (paused || clearActive) null else state.activeProgress,
                bulkTotal = if (activeBatch != null) shared.bulkTotal else 0,
                bulkDownloaded = if (activeBatch != null) bulkDone else 0,
                activeBatchId = activeBatch,
                pausedForNetwork = paused,
            )
        }
    }

    fun maybeFinishBulkBatchLocked(bulkDone: Int) {
        val bulkBatch = shared.bulkBatchId ?: return
        val completedInBatch = bulkDone - shared.bulkSkipped
        if (!DownloadQueuePolicy.isBulkBatchComplete(
                shared.bulkSkipped,
                shared.bulkTotal,
                bulkBatch,
                completedInBatch,
            )
        ) {
            return
        }
        shared.bulkBatchId = null
        shared.bulkTotal = 0
        shared.bulkSkipped = 0
    }

    fun addCompletedHistory(entity: DownloadQueueEntity) {
        val completed = DownloadQueueItem(
            bookId = entity.bookId,
            trackId = entity.trackId,
            title = entity.title,
            subtitle = entity.subtitle,
            contentType = entity.contentType,
            status = DownloadQueueItemStatus.COMPLETED,
            progress = 1f,
            batchId = entity.batchId,
            enqueuedAt = entity.enqueuedAt,
            completedAt = System.currentTimeMillis(),
        )
        shared.notifier.update { state ->
            state.copy(
                completedHistory = state.completedHistory + completed,
                activeBookId = null,
                activeTrackId = null,
                activeProgress = null,
                bulkDownloaded = if (entity.batchId != null && entity.batchId == shared.bulkBatchId) {
                    state.bulkDownloaded + 1
                } else {
                    state.bulkDownloaded
                },
            )
        }
    }

    suspend fun stopServiceIfIdle() {
        if (shared.downloadQueueRepository.getAll().isNotEmpty()) return
        val active = shared.notifier.snapshot()
        val bulkBatch = shared.bulkBatchId
        if (bulkBatch != null) {
            val completedInBatch = active.completedHistory.count { it.batchId == bulkBatch }
            val bulkDone = DownloadQueuePolicy.computeBulkDownloaded(
                shared.bulkSkipped,
                bulkBatch,
                completedInBatch,
            )
            maybeFinishBulkBatchLocked(bulkDone)
        }
        shared.workerJob?.cancel()
        shared.workerJob = null
        withContext(Dispatchers.Main) {
            shared.context.stopService(Intent(shared.context, TrackDownloadService::class.java))
        }
    }
}
