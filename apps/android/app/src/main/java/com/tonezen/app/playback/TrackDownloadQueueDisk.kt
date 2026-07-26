package com.tonezen.app.playback

import com.tonezen.app.data.local.SafeLocalStorage
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadQueueBookIdPolicy
import com.tonezen.app.domain.downloads.DownloadQueueKey

internal class TrackDownloadQueueDisk(
    private val shared: TrackDownloadQueueShared,
) {
    suspend fun purgeQueueEntry(bookId: String, trackId: String) {
        val key = DownloadQueueKey(bookId, trackId)
        shared.userCancelledKeys.remove(key)
        shared.failureCounts.remove(key)
        shared.downloadQueueRepository.delete(bookId, trackId)
    }

    suspend fun removeStaleQueueEntriesForTrack(trackId: String) {
        val canonical = shared.catalogRepository.canonicalBookIdForTrack(trackId) ?: return
        shared.downloadQueueRepository.getAll()
            .filter { it.trackId == trackId && DownloadQueueBookIdPolicy.isStaleQueueEntry(it.bookId, canonical) }
            .forEach { entity ->
                val staleKey = DownloadQueueKey(entity.bookId, entity.trackId)
                shared.userCancelledKeys.remove(staleKey)
                shared.failureCounts.remove(staleKey)
                shared.downloadQueueRepository.delete(entity.bookId, entity.trackId)
                shared.completeAwaiter(staleKey, DownloadAwaitResult.CANCELLED)
            }
    }

    suspend fun isTrackAlreadyOnDisk(bookId: String, trackId: String): Pair<String, String>? {
        val canonicalBookId = shared.catalogRepository.canonicalBookIdForTrack(trackId) ?: bookId
        shared.catalogRepository.resolveLocalTrackPath(canonicalBookId, trackId)?.let { path ->
            return canonicalBookId to path
        }
        if (canonicalBookId != bookId) {
            shared.catalogRepository.resolveLocalTrackPath(bookId, trackId)?.let { path ->
                return bookId to path
            }
        }
        return SafeLocalStorage.findDownloadedTrack(shared.context.filesDir, trackId, canonicalBookId)
            ?.let { it.bookId to it.path }
    }

    suspend fun completeTrackIfOnDisk(
        bookId: String,
        trackId: String,
        purgeQueue: Boolean,
    ): Boolean {
        val onDisk = isTrackAlreadyOnDisk(bookId, trackId) ?: return false
        val (diskBookId, path) = onDisk
        shared.catalogRepository.markTrackDownloaded(diskBookId, trackId, path)
        if (purgeQueue) {
            purgeQueueEntry(bookId, trackId)
            removeStaleQueueEntriesForTrack(trackId)
        }
        shared.localLibraryNotifier.notifyLocalLibraryChanged()
        shared.completeAwaiter(DownloadQueueKey(bookId, trackId), DownloadAwaitResult.COMPLETED)
        return true
    }

    suspend fun persistActivePartProgress() {
        val active = shared.notifier.snapshot()
        val bookId = active.activeBookId ?: return
        val trackId = active.activeTrackId ?: return
        persistPartProgress(bookId, trackId)
    }

    suspend fun persistPartProgress(bookId: String, trackId: String) {
        val part = SafeLocalStorage.trackPartFile(shared.context.filesDir, bookId, trackId) ?: return
        val length = if (part.exists()) part.length() else 0L
        shared.downloadQueueRepository.updateProgress(bookId, trackId, length, null, part.absolutePath)
    }
}
