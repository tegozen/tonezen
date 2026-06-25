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
    private val trackDownloadLocks: TrackDownloadLocks,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private var workerJob: Job? = null
    private var pausedForNetwork = false
    private var userCancelledKeys = mutableSetOf<DownloadQueueKey>()
    private val awaiters = ConcurrentHashMap<DownloadQueueKey, CompletableDeferred<DownloadAwaitResult>>()
    private var bulkBatchId: String? = null
    private var bulkTotal: Int = 0
    private var bulkSkipped: Int = 0
    private val failureCounts = mutableMapOf<DownloadQueueKey, Int>()

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
                var skipped = 0
                requests.forEach { request ->
                    val req = request.copy(batchId = batchId)
                    if (!SafeLocalStorage.isSafeId(req.bookId) || !SafeLocalStorage.isSafeId(req.trackId)) {
                        return@forEach
                    }
                    val onDisk = isTrackAlreadyOnDisk(req.bookId, req.trackId)
                    if (onDisk != null) {
                        val (diskBookId, path) = onDisk
                        catalogRepository.markTrackDownloaded(diskBookId, req.trackId, path)
                        skipped++
                        localLibraryNotifier.notifyLocalLibraryChanged()
                        completeAwaiter(DownloadQueueKey(req.bookId, req.trackId), DownloadAwaitResult.COMPLETED)
                    } else {
                        enqueueLocked(req, refreshNotifier = false)
                    }
                }
                bulkSkipped = skipped
                refreshNotifierFromDb()
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
        isTrackAlreadyOnDisk(bookId, trackId)?.let { (diskBookId, path) ->
            catalogRepository.markTrackDownloaded(diskBookId, trackId, path)
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

    fun cancelQueuedTrack(bookId: String, trackId: String) {
        if (!SafeLocalStorage.isSafeId(bookId) || !SafeLocalStorage.isSafeId(trackId)) return
        scope.launch {
            mutex.withLock {
                cancelQueuedTrackLocked(bookId, trackId)
                downloadRepository.cancelActiveDownload()
                refreshNotifierFromDb()
                stopServiceIfIdle()
            }
        }
    }

    suspend fun cancelMusicPlaybackDownloadsAwait() {
        mutex.withLock {
            val playbackPriorities = setOf(DownloadPriority.PLAY.name, DownloadPriority.PREFETCH.name)
            val items = downloadQueueDao.getAll().filter { entity ->
                entity.contentType == MUSIC_CONTENT_TYPE &&
                    entity.priority in playbackPriorities
            }
            if (items.isEmpty()) return@withLock
            items.forEach { cancelQueuedTrackLocked(it.bookId, it.trackId) }
            downloadRepository.cancelActiveDownload()
            refreshNotifierFromDb()
            stopServiceIfIdle()
        }
    }

    fun cancelTrack(bookId: String, trackId: String) {
        if (!SafeLocalStorage.isSafeId(bookId) || !SafeLocalStorage.isSafeId(trackId)) return
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
                    bulkSkipped = 0
                }
                refreshNotifierFromDb()
                stopServiceIfIdle()
            }
        }
    }

    fun cancelAll() {
        scope.launch {
            cancelAllAwait()
        }
    }

    suspend fun cancelAllAwait() {
        mutex.withLock {
            downloadRepository.cancelActiveDownload()
            val items = downloadQueueDao.getAll()
            items.forEach { cancelTrackLocked(it.bookId, it.trackId) }
            downloadQueueDao.deleteAll()
            userCancelledKeys.clear()
            bulkBatchId = null
            bulkTotal = 0
            bulkSkipped = 0
            refreshNotifierFromDb()
            stopServiceIfIdle()
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
                val onDisk = isTrackAlreadyOnDisk(entity.bookId, entity.trackId)
                if (onDisk != null) {
                    val (diskBookId, path) = onDisk
                    catalogRepository.markTrackDownloaded(diskBookId, entity.trackId, path)
                    downloadQueueDao.delete(entity.bookId, entity.trackId)
                }
            }
            refreshNotifierFromDb()
            if (networkMonitor.isOnline()) startWorkerLocked()
        }
    }

    private suspend fun enqueueLocked(
        request: EnqueueDownloadRequest,
        refreshNotifier: Boolean = true,
    ) {
        if (!SafeLocalStorage.isSafeId(request.bookId) || !SafeLocalStorage.isSafeId(request.trackId)) return
        val requestKey = DownloadQueueKey(request.bookId, request.trackId)
        userCancelledKeys.remove(requestKey)
        if (request.priority == DownloadPriority.USER || request.priority == DownloadPriority.PLAY) {
            failureCounts.remove(requestKey)
        }
        if (completeTrackIfOnDisk(request.bookId, request.trackId, purgeQueue = true)) return
        if (catalogRepository.resolveLocalTrackPath(request.bookId, request.trackId) != null) {
            completeAwaiter(requestKey, DownloadAwaitResult.COMPLETED)
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
        if (refreshNotifier) {
            refreshNotifierFromDb()
        }
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
            val disk = isTrackAlreadyOnDisk(next.bookId, next.trackId)
            if (disk != null) {
                val (diskBookId, path) = disk
                catalogRepository.markTrackDownloaded(diskBookId, next.trackId, path)
                failureCounts.remove(key)
                localLibraryNotifier.notifyLocalLibraryChanged()
                mutex.withLock {
                    purgeQueueForTrackId(next.trackId)
                    if (next.batchId != null && next.batchId == bulkBatchId) {
                        addCompletedHistory(next)
                    }
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
                        failureCounts.remove(key)
                        purgeQueueForTrackId(next.trackId)
                        addCompletedHistory(next)
                        localLibraryNotifier.notifyLocalLibraryChanged()
                    }
                    DownloadAwaitResult.CANCELLED -> {
                        failureCounts.remove(key)
                        downloadQueueDao.delete(next.bookId, next.trackId)
                    }
                    DownloadAwaitResult.FAILED, DownloadAwaitResult.OFFLINE -> {
                        val diskAfter = isTrackAlreadyOnDisk(next.bookId, next.trackId)
                        if (diskAfter != null) {
                            val (diskBookId, path) = diskAfter
                            catalogRepository.markTrackDownloaded(diskBookId, next.trackId, path)
                            failureCounts.remove(key)
                            purgeQueueForTrackId(next.trackId)
                            if (next.batchId != null && next.batchId == bulkBatchId) {
                                addCompletedHistory(next)
                            }
                            completeAwaiter(key, DownloadAwaitResult.COMPLETED)
                            localLibraryNotifier.notifyLocalLibraryChanged()
                        } else when (result) {
                            DownloadAwaitResult.OFFLINE -> {
                                persistPartProgress(next.bookId, next.trackId)
                                completeAwaiter(key, DownloadAwaitResult.OFFLINE)
                            }
                            else -> {
                                val attempts = (failureCounts[key] ?: 0) + 1
                                failureCounts[key] = attempts
                                if (attempts >= MAX_DOWNLOAD_FAILURES) {
                                    failureCounts.remove(key)
                                    downloadQueueDao.delete(next.bookId, next.trackId)
                                    completeAwaiter(key, DownloadAwaitResult.FAILED)
                                } else {
                                    persistPartProgress(next.bookId, next.trackId)
                                }
                            }
                        }
                    }
                }
                if (result != DownloadAwaitResult.FAILED && result != DownloadAwaitResult.OFFLINE) {
                    completeAwaiter(key, result)
                }
                refreshNotifierFromDb()
            }
            if (result == DownloadAwaitResult.OFFLINE) break
            delay(50)
        }
        mutex.withLock { stopServiceIfIdle() }
    }

    private suspend fun completeTrackIfOnDisk(
        bookId: String,
        trackId: String,
        purgeQueue: Boolean,
    ): Boolean {
        val onDisk = isTrackAlreadyOnDisk(bookId, trackId) ?: return false
        val (diskBookId, path) = onDisk
        catalogRepository.markTrackDownloaded(diskBookId, trackId, path)
        if (purgeQueue) {
            purgeQueueForTrackId(trackId)
        }
        localLibraryNotifier.notifyLocalLibraryChanged()
        completeAwaiter(DownloadQueueKey(bookId, trackId), DownloadAwaitResult.COMPLETED)
        return true
    }

    private suspend fun purgeQueueForTrackId(trackId: String) {
        val pending = downloadQueueDao.getAll().filter { it.trackId == trackId }
        if (pending.isEmpty()) return
        pending.forEach { entity ->
            val key = DownloadQueueKey(entity.bookId, entity.trackId)
            userCancelledKeys.remove(key)
            failureCounts.remove(key)
            downloadQueueDao.delete(entity.bookId, entity.trackId)
            completeAwaiter(key, DownloadAwaitResult.COMPLETED)
        }
    }

    private suspend fun isTrackAlreadyOnDisk(bookId: String, trackId: String): Pair<String, String>? {
        catalogRepository.resolveLocalTrackPath(bookId, trackId)?.let { path ->
            return bookId to path
        }
        return SafeLocalStorage.findDownloadedTrack(context.filesDir, trackId, bookId)
            ?.let { it.bookId to it.path }
    }

    private suspend fun downloadOne(entity: DownloadQueueEntity, key: DownloadQueueKey): DownloadAwaitResult =
        trackDownloadLocks.forTrack(entity.trackId).withLock {
            downloadOneLocked(entity, key)
        }

    private suspend fun downloadOneLocked(entity: DownloadQueueEntity, key: DownloadQueueKey): DownloadAwaitResult {
        if (userCancelledKeys.contains(key)) return DownloadAwaitResult.CANCELLED
        isTrackAlreadyOnDisk(entity.bookId, entity.trackId)?.let { (diskBookId, path) ->
            catalogRepository.markTrackDownloaded(diskBookId, entity.trackId, path)
            localLibraryNotifier.notifyLocalLibraryChanged()
            return DownloadAwaitResult.COMPLETED
        }
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
            if (!marked) {
                isTrackAlreadyOnDisk(entity.bookId, entity.trackId)?.let { (diskBookId, path) ->
                    catalogRepository.markTrackDownloaded(diskBookId, entity.trackId, path)
                }
                catalogRepository.reconcileLocalDownloadPaths()
                if (!outcome.finalFile.isFile || outcome.finalFile.length() <= 0L) {
                    return DownloadAwaitResult.FAILED
                }
            }
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

    private suspend fun cancelQueuedTrackLocked(bookId: String, trackId: String) {
        val key = DownloadQueueKey(bookId, trackId)
        userCancelledKeys.add(key)
        downloadQueueDao.delete(bookId, trackId)
        failureCounts.remove(key)
        completeAwaiter(key, DownloadAwaitResult.CANCELLED)
    }

    private suspend fun cancelTrackLocked(bookId: String, trackId: String) {
        val key = DownloadQueueKey(bookId, trackId)
        userCancelledKeys.add(key)
        downloadQueueDao.delete(bookId, trackId)
        downloadRepository.deleteLocalTrack(bookId, trackId)
        failureCounts.remove(key)
        completeAwaiter(key, DownloadAwaitResult.CANCELLED)
    }

    private suspend fun refreshNotifierFromDb(paused: Boolean = pausedForNetwork) {
        val rows = downloadQueueDao.getAll()
        val active = notifier.snapshot()
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
        val bulkBatch = bulkBatchId
        val completedInBatch = if (bulkBatch != null) {
            active.completedHistory.count { it.batchId == bulkBatch }
        } else {
            0
        }
        val bulkDone = DownloadQueuePolicy.computeBulkDownloaded(bulkSkipped, bulkBatch, completedInBatch)
        maybeFinishBulkBatchLocked(bulkDone)
        val activeBatch = bulkBatchId
        val activeStillQueued = active.activeTrackId != null && rows.any {
            it.bookId == active.activeBookId && it.trackId == active.activeTrackId
        }
        val clearActive = rows.isEmpty() || !activeStillQueued
        notifier.update { state ->
            state.copy(
                queuedItems = queued,
                activeBookId = if (paused || clearActive) null else state.activeBookId,
                activeTrackId = if (paused || clearActive) null else state.activeTrackId,
                activeProgress = if (paused || clearActive) null else state.activeProgress,
                bulkTotal = if (activeBatch != null) bulkTotal else 0,
                bulkDownloaded = if (activeBatch != null) bulkDone else 0,
                activeBatchId = activeBatch,
                pausedForNetwork = paused,
            )
        }
    }

    private fun maybeFinishBulkBatchLocked(bulkDone: Int) {
        val bulkBatch = bulkBatchId ?: return
        val completedInBatch = bulkDone - bulkSkipped
        if (!DownloadQueuePolicy.isBulkBatchComplete(bulkSkipped, bulkTotal, bulkBatch, completedInBatch)) {
            return
        }
        bulkBatchId = null
        bulkTotal = 0
        bulkSkipped = 0
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
        val active = notifier.snapshot()
        val bulkBatch = bulkBatchId
        if (bulkBatch != null) {
            val completedInBatch = active.completedHistory.count { it.batchId == bulkBatch }
            val bulkDone = DownloadQueuePolicy.computeBulkDownloaded(bulkSkipped, bulkBatch, completedInBatch)
            maybeFinishBulkBatchLocked(bulkDone)
        }
        workerJob?.cancel()
        workerJob = null
        withContext(Dispatchers.Main) {
            context.stopService(Intent(context, TrackDownloadService::class.java))
        }
    }

    companion object {
        private const val STATUS_QUEUED = "queued"
        private const val MAX_DOWNLOAD_FAILURES = 3
        private const val MUSIC_CONTENT_TYPE = "music"
    }
}
