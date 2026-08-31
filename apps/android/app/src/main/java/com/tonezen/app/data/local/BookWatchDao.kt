package com.tonezen.app.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface BookWatchDao {
    @Query("SELECT * FROM book_watch_events ORDER BY firstSeenAt DESC")
    fun observeEvents(): Flow<List<BookWatchEventEntity>>

    @Query("SELECT * FROM book_watches ORDER BY displayTitle")
    fun observeWatches(): Flow<List<BookWatchEntity>>

    @Upsert suspend fun upsertEvents(items: List<BookWatchEventEntity>)
    @Upsert suspend fun upsertWatches(items: List<BookWatchEntity>)

    @Query("UPDATE book_watch_events SET readAt = :readAt WHERE id IN (:ids)")
    suspend fun markRead(ids: List<String>, readAt: Long)
}
