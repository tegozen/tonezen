package com.tonezen.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface DownloadQueueDao {
    @Query("SELECT * FROM download_queue ORDER BY enqueuedAt ASC")
    suspend fun getAll(): List<DownloadQueueEntity>

    @Query("SELECT * FROM download_queue WHERE bookId = :bookId AND trackId = :trackId LIMIT 1")
    suspend fun get(bookId: String, trackId: String): DownloadQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: DownloadQueueEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<DownloadQueueEntity>)

    @Query("DELETE FROM download_queue WHERE bookId = :bookId AND trackId = :trackId")
    suspend fun delete(bookId: String, trackId: String)

    @Query("DELETE FROM download_queue WHERE batchId = :batchId")
    suspend fun deleteByBatch(batchId: String)

    @Query("DELETE FROM download_queue")
    suspend fun deleteAll()

    @Query(
        """
        UPDATE download_queue
        SET bytesDownloaded = :bytesDownloaded, totalBytes = :totalBytes, tempPath = :tempPath
        WHERE bookId = :bookId AND trackId = :trackId
        """,
    )
    suspend fun updateProgress(
        bookId: String,
        trackId: String,
        bytesDownloaded: Long,
        totalBytes: Long?,
        tempPath: String?,
    )
}
