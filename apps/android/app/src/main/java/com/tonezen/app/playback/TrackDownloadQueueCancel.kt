package com.tonezen.app.playback

import com.tonezen.app.data.local.SafeLocalStorage
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.DownloadQueueKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

internal class TrackDownloadQueueCancel(
    private val shared: TrackDownloadQueueShared,
    private val notify: TrackDownloadQueueNotify,
    private val disk: TrackDownloadQueueDisk,
) {
    fun cancelQueuedTrack(bookId: String, trackId: String) {
        if (!SafeLocalStorage.isSafeId(bookId) || !SafeLocalStorage.isSafeId(trackId)) return
        shared.scope.launch {
            shared.mutex.withLock {
                cancelQueuedTrackLocked(bookId, trackId)
                cancelActiveDownloadIfMatching(bookId, trackId)
                notify.refreshNotifierFromDb()
                notify.stopServiceIfIdle()
            }
        }
    }

    suspend fun cancelMusicPlaybackDownloadsAwait() {
        shared.mutex.withLock {
            val playbackPriorities = setOf(DownloadPriority.PLAY.name, DownloadPriority.PREFETCH.name)
            val items = shared.downloadQueueRepository.getAll().filter { entity ->
                entity.contentType == MUSIC_CONTENT_TYPE &&
                    entity.priority in playbackPriorities
            }
            if (items.isEmpty()) return@withLock
            val keys = items.map { DownloadQueueKey(it.bookId, it.trackId) }
            items.forEach { cancelQueuedTrackLocked(it.bookId, it.trackId) }
            cancelActiveDownloadIfMatchingAny(keys)
            notify.refreshNotifierFromDb()
            notify.stopServiceIfIdle()
        }
    }

    fun cancelTrack(bookId: String, trackId: String) {
        if (!SafeLocalStorage.isSafeId(bookId) || !SafeLocalStorage.isSafeId(trackId)) return
        shared.scope.launch {
            shared.mutex.withLock {
                val key = DownloadQueueKey(bookId, trackId)
                shared.userCancelledKeys.add(key)
                shared.downloadQueueRepository.delete(bookId, trackId)
                cancelActiveDownloadIfMatching(bookId, trackId)
                withContext(Dispatchers.IO) {
                    shared.downloadRepository.deleteLocalTrack(bookId, trackId)
                }
                shared.completeAwaiter(key, DownloadAwaitResult.CANCELLED)
                notify.refreshNotifierFromDb()
                notify.stopServiceIfIdle()
            }
        }
    }

    fun cancelBatch(batchId: String) {
        shared.scope.launch {
            shared.mutex.withLock {
                val items = shared.downloadQueueRepository.getAll().filter { it.batchId == batchId }
                items.forEach { cancelTrackLocked(it.bookId, it.trackId) }
                if (shared.bulkBatchId == batchId) {
                    shared.bulkBatchId = null
                    shared.bulkTotal = 0
                    shared.bulkSkipped = 0
                }
                notify.refreshNotifierFromDb()
                notify.stopServiceIfIdle()
            }
        }
    }

    fun cancelAll() {
        shared.scope.launch {
            cancelAllAwait()
        }
    }

    suspend fun cancelAllAwait() {
        shared.mutex.withLock {
            shared.downloadRepository.cancelActiveDownload()
            val items = shared.downloadQueueRepository.getAll()
            items.forEach { cancelTrackLocked(it.bookId, it.trackId) }
            shared.downloadQueueRepository.deleteAll()
            shared.userCancelledKeys.clear()
            shared.bulkBatchId = null
            shared.bulkTotal = 0
            shared.bulkSkipped = 0
            notify.refreshNotifierFromDb()
            notify.stopServiceIfIdle()
        }
    }

    fun pauseForNetwork() {
        shared.scope.launch {
            shared.mutex.withLock {
                if (shared.pausedForNetwork) return@withLock
                shared.pausedForNetwork = true
                shared.downloadRepository.cancelActiveDownload()
                disk.persistActivePartProgress()
                notify.refreshNotifierFromDb(paused = true)
            }
        }
    }

    fun resumeWhenOnline(startWorker: () -> Unit) {
        shared.scope.launch {
            shared.mutex.withLock {
                if (!shared.networkMonitor.isOnline()) return@withLock
                shared.pausedForNetwork = false
                notify.refreshNotifierFromDb(paused = false)
                startWorker()
            }
        }
    }

    private suspend fun cancelQueuedTrackLocked(bookId: String, trackId: String) {
        val key = DownloadQueueKey(bookId, trackId)
        shared.userCancelledKeys.add(key)
        shared.downloadQueueRepository.delete(bookId, trackId)
        shared.failureCounts.remove(key)
        shared.completeAwaiter(key, DownloadAwaitResult.CANCELLED)
    }

    private suspend fun cancelTrackLocked(bookId: String, trackId: String) {
        val key = DownloadQueueKey(bookId, trackId)
        shared.userCancelledKeys.add(key)
        shared.downloadQueueRepository.delete(bookId, trackId)
        shared.downloadRepository.deleteLocalTrack(bookId, trackId)
        shared.failureCounts.remove(key)
        shared.completeAwaiter(key, DownloadAwaitResult.CANCELLED)
    }

    private fun cancelActiveDownloadIfMatching(bookId: String, trackId: String) {
        val active = shared.notifier.snapshot()
        if (active.activeBookId == bookId && active.activeTrackId == trackId) {
            shared.downloadRepository.cancelActiveDownload()
        }
    }

    private fun cancelActiveDownloadIfMatchingAny(keys: Collection<DownloadQueueKey>) {
        val active = shared.notifier.snapshot()
        val activeBookId = active.activeBookId ?: return
        val activeTrackId = active.activeTrackId ?: return
        if (keys.any { it.bookId == activeBookId && it.trackId == activeTrackId }) {
            shared.downloadRepository.cancelActiveDownload()
        }
    }

    companion object {
        private const val MUSIC_CONTENT_TYPE = "music"
    }
}
