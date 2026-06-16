package com.tonezen.app.playback

import android.content.Context
import android.content.Intent
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.DownloadQueueDao
import com.tonezen.app.data.local.DownloadQueueEntity
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.SafeLocalStorage
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.DownloadQueueKey
import com.tonezen.app.domain.downloads.DownloadQueuePolicy
import com.tonezen.app.domain.downloads.DownloadQueueSortable
import com.tonezen.app.domain.downloads.EnqueueDownloadRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class TrackDownloadQueueController @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadQueueDao: DownloadQueueDao,
    private val catalogRepository: CatalogRepository,
    private val downloadRepository: DownloadRepository,
    private val sessionRepository: SessionRepository,
    private val networkMonitor: NetworkMonitor,
    private val notifier: DownloadQueueNotifier,
    private val localLibraryNotifier: LocalLibraryNotifier,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var workerJob: Job? = null
    private var pausedForNetwork = false
    private var userCancelledKeys = mutableSetOf<DownloadQueueKey>()
    private val awaiters = ConcurrentHashMap<DownloadQueueKey, CompletableDeferred<DownloadAwaitResult>>()
    private var bulkBatchId: String? = null
    private var bulkTotal: Int = 0

    init {
        scope.launch {
            restoreFromDb()
            networkMonitor.online.collectLatest { online ->
                if (online) {
                    pausedForNetwork = false
                    resumeWhenOnline()
                } else {
                    pauseForNetwork()
                }
            }
        }
    }

    fun enqueue(request: EnqueueDownloadRequest) {
        scope.launch {
            mutex.withLock {
                enqueueLocked(request)
            }
        }
    }

    fun enqueueBatch(requests: List<EnqueueDownloadRequest>, batchId: String = UUID.randomUUID().toString()) {
        if (requests.isEmpty()) return
        scope.launch {
            mutex.withLock {
                bulkBatchId = batchId
                bulkTotal = requests.size
                requests.forEach { enqueueLocked(it.copy(batchId = batchId)) }
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
        val key = DownloadQueueKey(bookId, trackId)
        if (catalogRepository.resolveLocalTrackPath(bookId, trackId) != null) {
            return DownloadAwaitResult.COMPLETED
        }
        val deferred = CompletableDeferred<DownloadAwaitResult>()
        awaiters[key] = deferred
        mutex.withLock {
            enqueueLocked(
                EnqueueDownloadRequest(
                    bookId = bookId,
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

    fun cancelTrack(bookId: String, trackId: String) {
        scope.launch {
            mutex.withLock {
                val key = DownloadQueueKey(bookId, trackId)
                userCancelledKeys.add(key)
                downloadQueueDao.delete(bookId, trackId)
                downloadRepository.cancelActiveDownload()
                withContext(Dispatchers.IO) {
                    downloadRepository.deleteLocalTrack(bookId, trackId)
                }
                completeAwaiter(key, DownloadAwaitResult.CANCELLED)
                refreshNotifierFromDb()
                stopServiceIfIdle()
            }
        }
    }

    fun cancelBatch(batchId: String) {
        scope.launch {
            mutex.withLock {
                val items = downloadQueueDao.getAll().filter { it.batchId == batchId }
                items.forEach { cancelTrackLocked(it.bookId, it.trackId) }
                if (bulkBatchId == batchId) {
                    bulkBatchId = null
                    bulkTotal = 0
                }
                refreshNotifierFromDb()
                stopServiceIfIdle()
            }
        }
    }

    fun cancelAll() {
        scope.launch {
            mutex.withLock {
                downloadRepository.cancelActiveDownload()
                val items = downloadQueueDao.getAll()
                items.forEach { cancelTrackLocked(it.bookId, it.trackId) }
                downloadQueueDao.deleteAll()
                bulkBatchId = null
                bulkTotal = 0
                refreshNotifierFromDb()
                stopServiceIfIdle()
            }
        }
    }

    fun pauseForNetwork() {
        scope.launch {
            mutex.withLock {
                if (pausedForNetwork) return@withLock
                pausedForNetwork = true
                downloadRepository.cancelActiveDownload()
                persistActivePartProgress()
                refreshNotifierFromDb(paused = true)
            }
        }
    }

    fun resumeWhenOnline() {
        scope.launch {
            mutex.withLock {
                if (!networkMonitor.isOnline()) return@withLock
                pausedForNetwork = false
                refreshNotifierFromDb(paused = false)
                startWorkerLocked()
            }
        }
    }

    suspend fun restoreFromDb() {
        mutex.withLock {
            val items = downloadQueueDao.getAll()
            items.forEach { entity ->
                if (catalogRepository.resolveLocalTrackPath(entity.bookId, entity.trackId) != null) {
                    downloadQueueDao.delete(entity.bookId, entity.trackId)
                }
            }
            refreshNotifierFromDb()
            if (networkMonitor.isOnline()) startWorkerLocked()
        }
    }

    private suspend fun enqueueLocked(request: EnqueueDownloadRequest) {
        if (!SafeLocalStorage.isSafeId(request.bookId) || !SafeLocalStorage.isSafeId(request.trackId)) return
        if (catalogRepository.resolveLocalTrackPath(request.bookId, request.trackId) != null) {
            completeAwaiter(DownloadQueueKey(request.bookId, request.trackId), DownloadAwaitResult.COMPLETED)
            return
        }
        val existing = downloadQueueDao.get(request.bookId, request.trackId)
        val priority = if (existing != null) {
            DownloadQueuePolicy.mergePriority(
                DownloadPriority.valueOf(existing.priority),
                request.priority,
            )
        } else {
            request.priority
        }
        val partFile = SafeLocalStorage.trackPartFile(context.filesDir, request.bookId, request.trackId)
        val entity = DownloadQueueEntity(
            bookId = request.bookId,
            trackId = request.trackId,
            priority = priority.name,
            batchId = request.batchId ?: existing?.batchId,
            enqueuedAt = existing?.enqueuedAt ?: request.enqueuedAt,
            title = request.title.ifBlank { existing?.title ?: request.trackId },
            subtitle = request.subtitle ?: existing?.subtitle,
            contentType = request.contentType,
            status = STATUS_QUEUED,
            bytesDownloaded = partFile?.takeIf { it.exists() }?.length() ?: existing?.bytesDownloaded ?: 0L,
            totalBytes = existing?.totalBytes,
            tempPath = partFile?.absolutePath,
        )
        downloadQueueDao.upsert(entity)
        refreshNotifierFromDb()
        startWorkerLocked()
    }

    private fun startWorkerLocked() {
        if (workerJob?.isActive == true) return
        if (pausedForNetwork || !networkMonitor.isOnline()) return
        context.startForegroundService(Intent(context, TrackDownloadService::class.java))
        workerJob = scope.launch {
            runWorker()
        }
    }

    private suspend fun runWorker() {
        while (true) {
            if (pausedForNetwork || !networkMonitor.isOnline()) break
            val next = mutex.withLock { pickNextLocked() } ?: break
            val key = DownloadQueueKey(next.bookId, next.trackId)
            if (userCancelledKeys.remove(key)) continue
            if (catalogRepository.resolveLocalTrackPath(next.bookId, next.trackId) != null) {
                mutex.withLock {
                    downloadQueueDao.delete(next.bookId, next.trackId)
                    completeAwaiter(key, DownloadAwaitResult.COMPLETED)
                    refreshNotifierFromDb()
                }
                continue
            }
            mutex.withLock {
                notifier.update { state ->
                    state.copy(
                        activeBookId = next.bookId,
                        activeTrackId = next.trackId,
                        activeProgress = 0f,
                        pausedForNetwork = false,
                    )
                }
            }
            val result = downloadOne(next, key)
            mutex.withLock {
                when (result) {
                    DownloadAwaitResult.COMPLETED -> {
                        downloadQueueDao.delete(next.bookId, next.trackId)
                        addCompletedHistory(next)
                    }
                    DownloadAwaitResult.CANCELLED -> downloadQueueDao.delete(next.bookId, next.trackId)
                    DownloadAwaitResult.FAILED, DownloadAwaitResult.OFFLINE -> {
                        persistPartProgress(next.bookId, next.trackId)
                    }
                }
                completeAwaiter(key, result)
                refreshNotifierFromDb()
            }
            if (result == DownloadAwaitResult.OFFLINE) break
            delay(50)
        }
        mutex.withLock { stopServiceIfIdle() }
    }

    private suspend fun downloadOne(entity: DownloadQueueEntity, key: DownloadQueueKey): DownloadAwaitResult {
        if (userCancelledKeys.contains(key)) return DownloadAwaitResult.CANCELLED
        return try {
            val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
                ?: return DownloadAwaitResult.FAILED
            var lastNotifyBucket = -1
            val partFile = SafeLocalStorage.trackPartFile(context.filesDir, entity.bookId, entity.trackId)
            val offset = partFile?.takeIf { it.exists() }?.length() ?: entity.bytesDownloaded
            val outcome = downloadRepository.downloadTrackResumable(
                accessToken = session.accessToken,
                bookId = entity.bookId,
                trackId = entity.trackId,
                bytesAlreadyDownloaded = offset,
                totalBytesHint = entity.totalBytes,
                onProgress = { progress ->
                    val bucket = (progress * 50).toInt()
                    if (bucket > lastNotifyBucket || progress >= 1f) {
                        lastNotifyBucket = bucket
                        notifier.update { state ->
                            state.copy(
                                activeBookId = entity.bookId,
                                activeTrackId = entity.trackId,
                                activeProgress = progress,
                            )
                        }
                    }
                },
                isCancelled = { userCancelledKeys.contains(key) || pausedForNetwork },
            )
            val marked = catalogRepository.markTrackDownloaded(
                entity.bookId,
                entity.trackId,
                outcome.finalFile.absolutePath,
            )
            if (!marked) return DownloadAwaitResult.FAILED
            localLibraryNotifier.notifyLocalLibraryChanged()
            DownloadAwaitResult.COMPLETED
        } catch (_: IOException) {
            if (userCancelledKeys.contains(key)) DownloadAwaitResult.CANCELLED
            else if (!networkMonitor.isOnline() || pausedForNetwork) DownloadAwaitResult.OFFLINE
            else DownloadAwaitResult.FAILED
        } catch (_: Exception) {
            DownloadAwaitResult.FAILED
        }
    }

    private suspend fun pickNextLocked(): DownloadQueueEntity? {
        val pending = downloadQueueDao.getAll().mapNotNull { entity ->
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

    private suspend fun persistActivePartProgress() {
        val active = notifier.snapshot()
        val bookId = active.activeBookId ?: return
        val trackId = active.activeTrackId ?: return
        persistPartProgress(bookId, trackId)
    }

    private suspend fun persistPartProgress(bookId: String, trackId: String) {
        val part = SafeLocalStorage.trackPartFile(context.filesDir, bookId, trackId) ?: return
        val length = if (part.exists()) part.length() else 0L
        downloadQueueDao.updateProgress(bookId, trackId, length, null, part.absolutePath)
    }

    private fun completeAwaiter(key: DownloadQueueKey, result: DownloadAwaitResult) {
        awaiters.remove(key)?.complete(result)
    }

    private suspend fun cancelTrackLocked(bookId: String, trackId: String) {
        val key = DownloadQueueKey(bookId, trackId)
        userCancelledKeys.add(key)
        downloadQueueDao.delete(bookId, trackId)
        downloadRepository.deleteLocalTrack(bookId, trackId)
        completeAwaiter(key, DownloadAwaitResult.CANCELLED)
    }

    private suspend fun refreshNotifierFromDb(paused: Boolean = pausedForNetwork) {
        val rows = downloadQueueDao.getAll()
        val active = notifier.snapshot()
        val queued = rows.map { entity ->
            DownloadQueueItem(
                bookId = entity.bookId,
                trackId = entity.trackId,
                title = entity.title,
                subtitle = entity.subtitle,
                contentType = entity.contentType,
                status = if (paused) {
                    DownloadQueueItemStatus.PAUSED_OFFLINE
                } else if (
                    entity.bookId == active.activeBookId && entity.trackId == active.activeTrackId
                ) {
                    DownloadQueueItemStatus.DOWNLOADING
                } else {
                    DownloadQueueItemStatus.QUEUED
                },
                progress = if (entity.bookId == active.activeBookId && entity.trackId == active.activeTrackId) {
                    active.activeProgress
                } else {
                    null
                },
                batchId = entity.batchId,
                enqueuedAt = entity.enqueuedAt,
                completedAt = null,
            )
        }.let { items ->
            DownloadQueuePolicy.sortPending(
                items.map { item ->
                    DownloadQueueSortable(
                        key = DownloadQueueKey(item.bookId, item.trackId),
                        priority = DownloadPriority.valueOf(
                            rows.first { it.trackId == item.trackId && it.bookId == item.bookId }.priority,
                        ),
                        enqueuedAt = item.enqueuedAt,
                    )
                },
            ).mapNotNull { sortable ->
                items.find { it.bookId == sortable.key.bookId && it.trackId == sortable.key.trackId }
            }
        }
        val bulkBatch = bulkBatchId
        val bulkDone = if (bulkBatch != null) {
            active.completedHistory.count { it.batchId == bulkBatch }
        } else {
            0
        }
        notifier.update { state ->
            state.copy(
                queuedItems = queued,
                activeBookId = if (paused) null else state.activeBookId,
                activeTrackId = if (paused) null else state.activeTrackId,
                activeProgress = if (paused) null else state.activeProgress,
                bulkTotal = if (bulkBatch != null) bulkTotal else 0,
                bulkDownloaded = bulkDone,
                activeBatchId = bulkBatch,
                pausedForNetwork = paused,
            )
        }
    }

    private fun addCompletedHistory(entity: DownloadQueueEntity) {
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
        notifier.update { state ->
            state.copy(
                completedHistory = state.completedHistory + completed,
                activeBookId = null,
                activeTrackId = null,
                activeProgress = null,
                bulkDownloaded = if (entity.batchId != null && entity.batchId == bulkBatchId) {
                    state.bulkDownloaded + 1
                } else {
                    state.bulkDownloaded
                },
            )
        }
    }

    private suspend fun stopServiceIfIdle() {
        if (downloadQueueDao.getAll().isNotEmpty()) return
        workerJob?.cancel()
        workerJob = null
        withContext(Dispatchers.Main) {
            context.stopService(Intent(context, TrackDownloadService::class.java))
        }
    }

    companion object {
        private const val STATUS_QUEUED = "queued"
    }
}
