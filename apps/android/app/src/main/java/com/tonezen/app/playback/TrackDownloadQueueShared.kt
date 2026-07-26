package com.tonezen.app.playback

import android.content.Context
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.DownloadQueueRepository
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadQueueKey
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.sync.Mutex
import java.util.concurrent.ConcurrentHashMap

/** Mutable runtime shared by download-queue collaborator modules. */
internal class TrackDownloadQueueShared(
    val context: Context,
    val downloadQueueRepository: DownloadQueueRepository,
    val catalogRepository: CatalogRepository,
    val downloadRepository: DownloadRepository,
    val sessionRepository: SessionRepository,
    val networkMonitor: NetworkMonitor,
    val notifier: DownloadQueueNotifier,
    val localLibraryNotifier: LocalLibraryNotifier,
    val trackDownloadLocks: TrackDownloadLocks,
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val mutex = Mutex()
    var workerJob: Job? = null
    var pausedForNetwork = false
    val userCancelledKeys = mutableSetOf<DownloadQueueKey>()
    val awaiters = ConcurrentHashMap<DownloadQueueKey, CompletableDeferred<DownloadAwaitResult>>()
    var bulkBatchId: String? = null
    var bulkTotal: Int = 0
    var bulkSkipped: Int = 0
    val failureCounts = mutableMapOf<DownloadQueueKey, Int>()

    fun completeAwaiter(key: DownloadQueueKey, result: DownloadAwaitResult) {
        awaiters.remove(key)?.complete(result)
    }
}
