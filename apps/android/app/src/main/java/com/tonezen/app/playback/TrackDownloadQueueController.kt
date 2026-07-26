package com.tonezen.app.playback

import android.content.Context
import android.content.Intent
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.DownloadQueueRepository
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.EnqueueDownloadRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID

@Singleton
class TrackDownloadQueueController @Inject constructor(
    @ApplicationContext context: Context,
    downloadQueueRepository: DownloadQueueRepository,
    catalogRepository: CatalogRepository,
    downloadRepository: DownloadRepository,
    sessionRepository: SessionRepository,
    networkMonitor: NetworkMonitor,
    notifier: DownloadQueueNotifier,
    localLibraryNotifier: LocalLibraryNotifier,
    trackDownloadLocks: TrackDownloadLocks,
) {
    private val shared = TrackDownloadQueueShared(
        context = context,
        downloadQueueRepository = downloadQueueRepository,
        catalogRepository = catalogRepository,
        downloadRepository = downloadRepository,
        sessionRepository = sessionRepository,
        networkMonitor = networkMonitor,
        notifier = notifier,
        localLibraryNotifier = localLibraryNotifier,
        trackDownloadLocks = trackDownloadLocks,
    )
    private val notify = TrackDownloadQueueNotify(shared)
    private val disk = TrackDownloadQueueDisk(shared)
    private val transfer = TrackDownloadQueueTransfer(shared, disk)
    private val worker = TrackDownloadQueueWorker(shared, notify, disk, transfer)
    private val startWorkerLocked: () -> Unit = {
        if (shared.workerJob?.isActive != true &&
            !shared.pausedForNetwork &&
            shared.networkMonitor.isOnline()
        ) {
            shared.context.startForegroundService(
                Intent(shared.context, TrackDownloadService::class.java),
            )
            shared.workerJob = shared.scope.launch {
                worker.runWorker()
            }
        }
    }
    private val enqueueOps = TrackDownloadQueueEnqueue(shared, notify, disk, startWorkerLocked)
    private val cancelOps = TrackDownloadQueueCancel(shared, notify, disk)

    init {
        shared.scope.launch {
            enqueueOps.restoreFromDb()
            networkMonitor.online.collectLatest { online ->
                if (online) {
                    shared.pausedForNetwork = false
                    cancelOps.resumeWhenOnline(startWorkerLocked)
                } else {
                    cancelOps.pauseForNetwork()
                }
            }
        }
    }

    fun enqueue(request: EnqueueDownloadRequest) = enqueueOps.enqueue(request)

    fun enqueueBatch(
        requests: List<EnqueueDownloadRequest>,
        batchId: String = UUID.randomUUID().toString(),
    ) = enqueueOps.enqueueBatch(requests, batchId)

    suspend fun awaitTrack(
        bookId: String,
        trackId: String,
        priority: DownloadPriority = DownloadPriority.PLAY,
        title: String = "",
        subtitle: String? = null,
        contentType: String = "music",
    ): DownloadAwaitResult = enqueueOps.awaitTrack(bookId, trackId, priority, title, subtitle, contentType)

    fun cancelQueuedTrack(bookId: String, trackId: String) = cancelOps.cancelQueuedTrack(bookId, trackId)

    suspend fun cancelMusicPlaybackDownloadsAwait() = cancelOps.cancelMusicPlaybackDownloadsAwait()

    fun cancelTrack(bookId: String, trackId: String) = cancelOps.cancelTrack(bookId, trackId)

    fun cancelBatch(batchId: String) = cancelOps.cancelBatch(batchId)

    fun cancelAll() = cancelOps.cancelAll()

    suspend fun cancelAllAwait() = cancelOps.cancelAllAwait()

    fun pauseForNetwork() = cancelOps.pauseForNetwork()

    fun resumeWhenOnline() = cancelOps.resumeWhenOnline(startWorkerLocked)

    suspend fun reconcileDownloadQueueBookIds() = enqueueOps.reconcileDownloadQueueBookIds()

    suspend fun restoreFromDb() = enqueueOps.restoreFromDb()
}
