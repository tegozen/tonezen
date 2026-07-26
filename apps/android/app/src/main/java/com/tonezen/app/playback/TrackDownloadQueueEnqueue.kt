package com.tonezen.app.playback

import android.content.Intent
import com.tonezen.app.data.local.DownloadQueueEntity
import com.tonezen.app.data.local.SafeLocalStorage
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.DownloadQueueBookIdPolicy
import com.tonezen.app.domain.downloads.DownloadQueueKey
import com.tonezen.app.domain.downloads.DownloadQueuePolicy
import com.tonezen.app.domain.downloads.EnqueueDownloadRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/** Enqueue / await / restore / reconcile for the download queue. */
internal class TrackDownloadQueueEnqueue(
    private val shared: TrackDownloadQueueShared,
    private val notify: TrackDownloadQueueNotify,
    private val disk: TrackDownloadQueueDisk,
    private val startWorker: () -> Unit,
) {
    fun enqueue(request: EnqueueDownloadRequest) {
        shared.scope.launch {
            shared.mutex.withLock {
                enqueueLocked(request)
            }
        }
    }

    fun enqueueBatch(requests: List<EnqueueDownloadRequest>, batchId: String = UUID.randomUUID().toString()) {
        if (requests.isEmpty()) return
        shared.scope.launch {
            shared.mutex.withLock {
                shared.bulkBatchId = batchId
                shared.bulkTotal = requests.size
                var skipped = 0
                var enqueueSequence = System.currentTimeMillis()
                requests.forEach { request ->
                    val normalized = normalizeEnqueueRequest(request)
                    val req = normalized.copy(
                        batchId = batchId,
                        enqueuedAt = enqueueSequence++,
                    )
                    if (!SafeLocalStorage.isSafeId(req.bookId) || !SafeLocalStorage.isSafeId(req.trackId)) {
                        return@forEach
                    }
                    val onDisk = disk.isTrackAlreadyOnDisk(req.bookId, req.trackId)
                    if (onDisk != null) {
                        val (diskBookId, path) = onDisk
                        shared.catalogRepository.markTrackDownloaded(diskBookId, req.trackId, path)
                        skipped++
                        shared.localLibraryNotifier.notifyLocalLibraryChanged()
                        shared.completeAwaiter(
                            DownloadQueueKey(req.bookId, req.trackId),
                            DownloadAwaitResult.COMPLETED,
                        )
                    } else {
                        enqueueLocked(req, refreshNotifier = false)
                    }
                }
                shared.bulkSkipped = skipped
                notify.refreshNotifierFromDb()
            }
        }
    }

    suspend fun awaitTrack(
        bookId: String,
        trackId: String,
        priority: DownloadPriority = DownloadPriority.PLAY,
        title: String = "",
        subtitle: String? = null,
        contentType: String = "music",
    ): DownloadAwaitResult {
        val normalizedBookId = DownloadQueueBookIdPolicy.resolveEnqueueBookId(
            bookId,
            shared.catalogRepository.canonicalBookIdForTrack(trackId),
        )
        val key = DownloadQueueKey(normalizedBookId, trackId)
        disk.isTrackAlreadyOnDisk(normalizedBookId, trackId)?.let { (diskBookId, path) ->
            shared.catalogRepository.markTrackDownloaded(diskBookId, trackId, path)
            return DownloadAwaitResult.COMPLETED
        }
        val deferred = CompletableDeferred<DownloadAwaitResult>()
        shared.awaiters[key] = deferred
        shared.mutex.withLock {
            enqueueLocked(
                EnqueueDownloadRequest(
                    bookId = normalizedBookId,
                    trackId = trackId,
                    priority = priority,
                    title = title,
                    subtitle = subtitle,
                    contentType = contentType,
                ),
            )
        }
        return deferred.await()
    }

    suspend fun reconcileDownloadQueueBookIds() {
        shared.mutex.withLock {
            reconcileDownloadQueueBookIdsLocked()
            notify.refreshNotifierFromDb()
        }
    }

    suspend fun restoreFromDb() {
        shared.mutex.withLock {
            reconcileDownloadQueueBookIdsLocked()
            val items = shared.downloadQueueRepository.getAll()
            items.forEach { entity ->
                val onDisk = disk.isTrackAlreadyOnDisk(entity.bookId, entity.trackId)
                if (onDisk != null) {
                    val (diskBookId, path) = onDisk
                    shared.catalogRepository.markTrackDownloaded(diskBookId, entity.trackId, path)
                    shared.downloadQueueRepository.delete(entity.bookId, entity.trackId)
                }
            }
            notify.refreshNotifierFromDb()
            if (shared.networkMonitor.isOnline()) startWorker()
        }
    }

    private suspend fun reconcileDownloadQueueBookIdsLocked() {
        val stale = shared.downloadQueueRepository.getAll().mapNotNull { entity ->
            val canonical = shared.catalogRepository.canonicalBookIdForTrack(entity.trackId)
                ?: return@mapNotNull null
            if (DownloadQueueBookIdPolicy.isStaleQueueEntry(entity.bookId, canonical)) {
                entity to canonical
            } else {
                null
            }
        }
        stale.forEach { (entity, canonical) ->
            val staleKey = DownloadQueueKey(entity.bookId, entity.trackId)
            shared.downloadQueueRepository.delete(entity.bookId, entity.trackId)
            shared.userCancelledKeys.remove(staleKey)
            shared.failureCounts.remove(staleKey)
            shared.completeAwaiter(staleKey, DownloadAwaitResult.CANCELLED)
            shared.downloadQueueRepository.upsert(entity.copy(bookId = canonical))
        }
    }

    private suspend fun enqueueLocked(
        request: EnqueueDownloadRequest,
        refreshNotifier: Boolean = true,
    ) {
        val normalized = normalizeEnqueueRequest(request)
        if (!SafeLocalStorage.isSafeId(normalized.bookId) || !SafeLocalStorage.isSafeId(normalized.trackId)) return
        val requestKey = DownloadQueueKey(normalized.bookId, normalized.trackId)
        shared.userCancelledKeys.remove(requestKey)
        if (normalized.priority == DownloadPriority.USER || normalized.priority == DownloadPriority.PLAY) {
            shared.failureCounts.remove(requestKey)
        }
        if (disk.completeTrackIfOnDisk(normalized.bookId, normalized.trackId, purgeQueue = true)) return
        if (shared.catalogRepository.resolveLocalTrackPath(normalized.bookId, normalized.trackId) != null) {
            shared.completeAwaiter(requestKey, DownloadAwaitResult.COMPLETED)
            return
        }
        val existing = shared.downloadQueueRepository.get(normalized.bookId, normalized.trackId)
        val priority = if (existing != null) {
            DownloadQueuePolicy.mergePriority(
                DownloadPriority.valueOf(existing.priority),
                normalized.priority,
            )
        } else {
            normalized.priority
        }
        val partFile = SafeLocalStorage.trackPartFile(
            shared.context.filesDir,
            normalized.bookId,
            normalized.trackId,
        )
        val entity = DownloadQueueEntity(
            bookId = normalized.bookId,
            trackId = normalized.trackId,
            priority = priority.name,
            batchId = normalized.batchId ?: existing?.batchId,
            enqueuedAt = existing?.enqueuedAt ?: normalized.enqueuedAt,
            title = normalized.title.ifBlank { existing?.title ?: normalized.trackId },
            subtitle = normalized.subtitle ?: existing?.subtitle,
            contentType = normalized.contentType,
            status = STATUS_QUEUED,
            bytesDownloaded = partFile?.takeIf { it.exists() }?.length() ?: existing?.bytesDownloaded ?: 0L,
            totalBytes = existing?.totalBytes,
            tempPath = partFile?.absolutePath,
        )
        shared.downloadQueueRepository.upsert(entity)
        if (refreshNotifier) {
            notify.refreshNotifierFromDb()
        }
        startWorker()
    }

    private suspend fun normalizeEnqueueRequest(request: EnqueueDownloadRequest): EnqueueDownloadRequest {
        val canonicalBookId = shared.catalogRepository.canonicalBookIdForTrack(request.trackId)
        val bookId = DownloadQueueBookIdPolicy.resolveEnqueueBookId(request.bookId, canonicalBookId)
        return if (bookId == request.bookId) request else request.copy(bookId = bookId)
    }

    companion object {
        private const val STATUS_QUEUED = "queued"
    }
}
