package com.tonezen.app.data.local

import com.tonezen.app.domain.downloads.DownloadQueueEntry
import javax.inject.Inject
import javax.inject.Singleton

/** Owns DownloadQueueDao so playback never injects Room DAOs. */
@Singleton
class DownloadQueueRepository @Inject constructor(
    private val downloadQueueDao: DownloadQueueDao,
) {
    suspend fun getAll(): List<DownloadQueueEntry> = downloadQueueDao.getAll().map { it.toDomain() }

    suspend fun get(bookId: String, trackId: String): DownloadQueueEntry? =
        downloadQueueDao.get(bookId, trackId)?.toDomain()

    suspend fun upsert(item: DownloadQueueEntry) = downloadQueueDao.upsert(item.toEntity())

    suspend fun upsertAll(items: List<DownloadQueueEntry>) = downloadQueueDao.upsertAll(items.map { it.toEntity() })

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
