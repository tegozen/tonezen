package com.tonezen.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CatalogDao {
    @Query("SELECT * FROM books ORDER BY title")
    suspend fun getAllBooks(): List<BookEntity>

    @Query("SELECT * FROM books WHERE id = :bookId LIMIT 1")
    suspend fun getBook(bookId: String): BookEntity?

    @Query("SELECT bookId FROM tracks WHERE id = :trackId LIMIT 1")
    suspend fun getBookIdForTrack(trackId: String): String?

    @Query("DELETE FROM tracks WHERE bookId NOT IN (:bookIds)")
    suspend fun deleteTracksForBooksNotIn(bookIds: List<String>)

    @Query("DELETE FROM books WHERE id NOT IN (:bookIds)")
    suspend fun deleteBooksNotIn(bookIds: List<String>)

    @Query("SELECT * FROM tracks WHERE bookId = :bookId ORDER BY sortOrder")
    suspend fun getTracksForBook(bookId: String): List<TrackEntity>

    @Query("SELECT * FROM tracks ORDER BY bookId, sortOrder")
    suspend fun getAllTracks(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE bookId IN (:bookIds) ORDER BY bookId, sortOrder")
    suspend fun getTracksForBooks(bookIds: List<String>): List<TrackEntity>

    @Query("SELECT DISTINCT bookId FROM tracks WHERE localPath IS NOT NULL AND localPath != ''")
    suspend fun getBookIdsWithDownloads(): List<String>

    @Query("SELECT * FROM tracks WHERE localPath IS NOT NULL AND localPath != ''")
    suspend fun getTracksWithLocalPath(): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE localPath IS NULL OR localPath = ''")
    suspend fun getTracksWithoutLocalPath(): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBooks(books: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("SELECT * FROM audiobook_progress WHERE bookId = :bookId")
    suspend fun getProgress(bookId: String): AudiobookProgressEntity?

    @Query("SELECT * FROM audiobook_progress WHERE bookId IN (:bookIds)")
    suspend fun getProgressForBooks(bookIds: List<String>): List<AudiobookProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: AudiobookProgressEntity)

    @Query("SELECT * FROM audiobook_progress WHERE pendingSync = 1")
    suspend fun getPendingProgress(): List<AudiobookProgressEntity>

    @Query("DELETE FROM audiobook_progress WHERE bookId = :bookId")
    suspend fun deleteProgress(bookId: String)

    @Query("UPDATE tracks SET localPath = NULL, localDownloadedAt = NULL WHERE bookId = :bookId")
    suspend fun clearLocalPathsForBook(bookId: String)

    @Query("SELECT * FROM cycles ORDER BY title")
    suspend fun getAllCycles(): List<CycleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCycles(cycles: List<CycleEntity>)

    @Query("DELETE FROM cycles WHERE id NOT IN (:cycleIds)")
    suspend fun deleteCyclesNotIn(cycleIds: List<String>)

    @Query(
        """
        SELECT * FROM tracks
        WHERE localDownloadedAt IS NOT NULL
        ORDER BY localDownloadedAt ASC
        LIMIT :limit
        """,
    )
    suspend fun getTracksOrderedByDownloadedAt(limit: Int): List<TrackEntity>
}