package com.tonezen.app.playback

import android.util.Log
import com.tonezen.app.data.local.DownloadQueueEntity
import com.tonezen.app.data.local.SafeLocalStorage
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadQueueKey
import java.io.IOException
import kotlinx.coroutines.sync.withLock

internal class TrackDownloadQueueTransfer(
    private val shared: TrackDownloadQueueShared,
    private val disk: TrackDownloadQueueDisk,
) {
    suspend fun downloadOne(entity: DownloadQueueEntity, key: DownloadQueueKey): DownloadAwaitResult =
        shared.trackDownloadLocks.forTrack(entity.trackId).withLock {
            downloadOneLocked(entity, key)
        }

    private suspend fun downloadOneLocked(entity: DownloadQueueEntity, key: DownloadQueueKey): DownloadAwaitResult {
        if (shared.userCancelledKeys.contains(key)) return DownloadAwaitResult.CANCELLED
        disk.isTrackAlreadyOnDisk(entity.bookId, entity.trackId)?.let { (diskBookId, path) ->
            shared.catalogRepository.markTrackDownloaded(diskBookId, entity.trackId, path)
            shared.localLibraryNotifier.notifyLocalLibraryChanged()
            return DownloadAwaitResult.COMPLETED
        }
        return try {
            val session = shared.sessionRepository.refreshIfNeeded(shared.sessionRepository.loadSession())
                ?: return DownloadAwaitResult.FAILED.also {
                    Log.w(TAG, "download failed: no session book=${entity.bookId} track=${entity.trackId}")
                }
            var lastNotifyBucket = -1
            val partFile = SafeLocalStorage.trackPartFile(shared.context.filesDir, entity.bookId, entity.trackId)
            val offset = partFile?.takeIf { it.exists() }?.length() ?: entity.bytesDownloaded
            val outcome = shared.downloadRepository.downloadTrackResumable(
                accessToken = session.accessToken,
                bookId = entity.bookId,
                trackId = entity.trackId,
                bytesAlreadyDownloaded = offset,
                totalBytesHint = entity.totalBytes,
                onProgress = { progress ->
                    val bucket = (progress * 50).toInt()
                    if (bucket > lastNotifyBucket || progress >= 1f) {
                        lastNotifyBucket = bucket
                        shared.notifier.update { state ->
                            state.copy(
                                activeBookId = entity.bookId,
                                activeTrackId = entity.trackId,
                                activeProgress = progress,
                            )
                        }
                    }
                },
                isCancelled = {
                    shared.userCancelledKeys.contains(key) || shared.pausedForNetwork
                },
            )
            val marked = shared.catalogRepository.markTrackDownloaded(
                entity.bookId,
                entity.trackId,
                outcome.finalFile.absolutePath,
            )
            if (!marked) {
                disk.isTrackAlreadyOnDisk(entity.bookId, entity.trackId)?.let { (diskBookId, path) ->
                    shared.catalogRepository.markTrackDownloaded(diskBookId, entity.trackId, path)
                }
                shared.catalogRepository.reconcileLocalDownloadPaths()
                if (!outcome.finalFile.isFile || outcome.finalFile.length() <= 0L) {
                    Log.w(TAG, "download failed: mark/path book=${entity.bookId} track=${entity.trackId}")
                    return DownloadAwaitResult.FAILED
                }
            }
            shared.localLibraryNotifier.notifyLocalLibraryChanged()
            DownloadAwaitResult.COMPLETED
        } catch (e: IOException) {
            Log.w(TAG, "download IO book=${entity.bookId} track=${entity.trackId}: ${e.message}")
            if (shared.userCancelledKeys.contains(key)) DownloadAwaitResult.CANCELLED
            else if (!shared.networkMonitor.isOnline() || shared.pausedForNetwork) DownloadAwaitResult.OFFLINE
            else DownloadAwaitResult.FAILED
        } catch (e: Exception) {
            Log.w(TAG, "download error book=${entity.bookId} track=${entity.trackId}: ${e.message}")
            DownloadAwaitResult.FAILED
        }
    }

    companion object {
        private const val TAG = "TonezenDownload"
    }
}
