package com.tonezen.app.data.local

import javax.inject.Inject
import javax.inject.Singleton

/** Owns DownloadQueueDao so playback never injects Room DAOs. */
@Singleton
class DownloadQueueRepository @Inject constructor(
    private val downloadQueueDao: DownloadQueueDao,
) {
    suspend fun getAll(): List<DownloadQueueEntity> = downloadQueueDao.getAll()

    suspend fun get(bookId: String, trackId: String): DownloadQueueEntity? =
        downloadQueueDao.get(bookId, trackId)

    suspend fun upsert(item: DownloadQueueEntity) = downloadQueueDao.upsert(item)

    suspend fun upsertAll(items: List<DownloadQueueEntity>) = downloadQueueDao.upsertAll(items)

    suspend fun delete(bookId: String, trackId: String) = downloadQueueDao.delete(bookId, trackId)

    suspend fun deleteByBatch(batchId: String) = downloadQueueDao.deleteByBatch(batchId)

    suspend fun deleteAll() = downloadQueueDao.deleteAll()

    suspend fun updateProgress(
        bookId: String,
        trackId: String,
        bytesDownloaded: Long,
        totalBytes: Long?,
        tempPath: String?,
    ) = downloadQueueDao.updateProgress(bookId, trackId, bytesDownloaded, totalBytes, tempPath)
}
