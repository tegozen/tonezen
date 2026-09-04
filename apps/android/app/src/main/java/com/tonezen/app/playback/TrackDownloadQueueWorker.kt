package com.tonezen.app.playback

import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.DownloadQueueEntry
import com.tonezen.app.domain.downloads.DownloadQueueKey
import com.tonezen.app.domain.downloads.DownloadQueuePolicy
import com.tonezen.app.domain.downloads.DownloadQueueSortable
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock

internal class TrackDownloadQueueWorker(
    private val shared: TrackDownloadQueueShared,
    private val notify: TrackDownloadQueueNotify,
    private val disk: TrackDownloadQueueDisk,
    private val transfer: TrackDownloadQueueTransfer,
) {
    suspend fun runWorker() {
        while (true) {
            if (shared.pausedForNetwork || !shared.networkMonitor.isOnline()) break
            val next = shared.mutex.withLock { pickNextLocked() } ?: break
            val key = DownloadQueueKey(next.bookId, next.trackId)
            if (shared.mutex.withLock { shared.userCancelledKeys.remove(key) }) continue
            val onDisk = disk.isTrackAlreadyOnDisk(next.bookId, next.trackId)
            if (onDisk != null) {
                val (diskBookId, path) = onDisk
                shared.catalogRepository.markTrackDownloaded(diskBookId, next.trackId, path)
                shared.failureCounts.remove(key)
                shared.localLibraryNotifier.notifyLocalLibraryChanged()
                shared.mutex.withLock {
                    disk.purgeQueueEntry(next.bookId, next.trackId)
                    disk.removeStaleQueueEntriesForTrack(next.trackId)
                    if (next.batchId != null && next.batchId == shared.bulkBatchId) {
                        notify.addCompletedHistory(next)
                    }
                    shared.completeAwaiter(key, DownloadAwaitResult.COMPLETED)
                    notify.refreshNotifierFromDb()
                }
                continue
            }
            var result: DownloadAwaitResult
            do {
                shared.mutex.withLock {
                    shared.notifier.update { state ->
                        state.copy(
                            activeBookId = next.bookId,
                            activeTrackId = next.trackId,
                            activeProgress = 0f,
                            pausedForNetwork = false,
                        )
                    }
                    notify.refreshNotifierFromDb()
                }
                result = transfer.downloadOne(next, key)
                shared.mutex.withLock {
                    handleDownloadResult(next, key, result)?.let { result = it }
                    if (result != DownloadAwaitResult.FAILED && result != DownloadAwaitResult.OFFLINE) {
                        shared.completeAwaiter(key, result)
                    }
                    notify.refreshNotifierFromDb()
                }
            } while (result == DownloadAwaitResult.FAILED && shared.failureCounts.containsKey(key))
            if (result == DownloadAwaitResult.OFFLINE) break
            delay(50)
        }
        shared.mutex.withLock { notify.stopServiceIfIdle() }
    }

    private suspend fun handleDownloadResult(
        next: DownloadQueueEntry,
        key: DownloadQueueKey,
        result: DownloadAwaitResult,
    ): DownloadAwaitResult? {
        when (result) {
            DownloadAwaitResult.COMPLETED -> {
                shared.failureCounts.remove(key)
                disk.purgeQueueEntry(next.bookId, next.trackId)
                disk.removeStaleQueueEntriesForTrack(next.trackId)
                notify.addCompletedHistory(next)
                shared.localLibraryNotifier.notifyLocalLibraryChanged()
            }
            DownloadAwaitResult.CANCELLED -> {
                shared.failureCounts.remove(key)
                shared.downloadQueueRepository.delete(next.bookId, next.trackId)
            }
            DownloadAwaitResult.FAILED, DownloadAwaitResult.OFFLINE -> {
                val diskAfter = disk.isTrackAlreadyOnDisk(next.bookId, next.trackId)
                if (diskAfter != null) {
                    val (diskBookId, path) = diskAfter
                    shared.catalogRepository.markTrackDownloaded(diskBookId, next.trackId, path)
                    shared.failureCounts.remove(key)
                    disk.purgeQueueEntry(next.bookId, next.trackId)
                    disk.removeStaleQueueEntriesForTrack(next.trackId)
                    if (next.batchId != null && next.batchId == shared.bulkBatchId) {
                        notify.addCompletedHistory(next)
                    }
                    shared.completeAwaiter(key, DownloadAwaitResult.COMPLETED)
                    shared.localLibraryNotifier.notifyLocalLibraryChanged()
                    return DownloadAwaitResult.COMPLETED
                }
                when (result) {
                    DownloadAwaitResult.OFFLINE -> {
                        disk.persistPartProgress(next.bookId, next.trackId)
                        shared.completeAwaiter(key, DownloadAwaitResult.OFFLINE)
                    }
                    else -> {
                        val attempts = (shared.failureCounts[key] ?: 0) + 1
                        shared.failureCounts[key] = attempts
                        if (attempts >= MAX_DOWNLOAD_FAILURES) {
                            shared.failureCounts.remove(key)
                            shared.downloadQueueRepository.delete(next.bookId, next.trackId)
                            shared.completeAwaiter(key, DownloadAwaitResult.FAILED)
                        } else {
                            disk.persistPartProgress(next.bookId, next.trackId)
                        }
                    }
                }
            }
        }
        return null
    }

    suspend fun pickNextLocked(): DownloadQueueEntry? {
        val pending = shared.downloadQueueRepository.getAll().mapNotNull { entity ->
            runCatching {
                DownloadQueueSortable(
                    key = DownloadQueueKey(entity.bookId, entity.trackId),
                    priority = DownloadPriority.valueOf(entity.priority),
                    enqueuedAt = entity.enqueuedAt,
                ) to entity
            }.getOrNull()
        }
        val sorted = DownloadQueuePolicy.sortPending(pending.map { it.first })
        val firstKey = sorted.firstOrNull()?.key ?: return null
        return pending.first { it.first.key == firstKey }.second
    }

    companion object {
        private const val MAX_DOWNLOAD_FAILURES = 3
    }
}
