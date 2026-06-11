package com.tplayer.app.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

@Entity(tableName = "books")
data class BookEntity(
    @PrimaryKey val id: String,
    val slug: String,
    val contentType: String,
    val title: String,
    val author: String?,
    val coverPath: String?,
    val updatedAt: String,
)

@Entity(tableName = "tracks")
data class TrackEntity(
    @PrimaryKey val id: String,
    val bookId: String,
    val sortOrder: Int,
    val title: String,
    val filename: String,
    val durationMs: Long?,
    val localPath: String?,
)

@Entity(tableName = "audiobook_progress")
data class AudiobookProgressEntity(
    @PrimaryKey val bookId: String,
    val trackId: String,
    val positionMs: Long,
    val updatedAtEpochMs: Long,
    val pendingSync: Boolean = false,
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val bookId: String,
    val pendingSync: Boolean = false,
)

@Dao
interface CatalogDao {
    @Query("SELECT * FROM books ORDER BY title")
    suspend fun getAllBooks(): List<BookEntity>

    @Query("SELECT * FROM tracks WHERE bookId = :bookId ORDER BY sortOrder")
    suspend fun getTracksForBook(bookId: String): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBooks(books: List<BookEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTracks(tracks: List<TrackEntity>)

    @Query("SELECT * FROM audiobook_progress WHERE bookId = :bookId")
    suspend fun getProgress(bookId: String): AudiobookProgressEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(progress: AudiobookProgressEntity)

    @Query("SELECT * FROM audiobook_progress WHERE pendingSync = 1")
    suspend fun getPendingProgress(): List<AudiobookProgressEntity>

    @Query("SELECT * FROM favorites")
    suspend fun getFavorites(): List<FavoriteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE bookId = :bookId")
    suspend fun deleteFavorite(bookId: String)

    @Query("UPDATE tracks SET localPath = NULL WHERE bookId = :bookId")
    suspend fun clearLocalPathsForBook(bookId: String)
}

@Database(
    entities = [BookEntity::class, TrackEntity::class, AudiobookProgressEntity::class, FavoriteEntity::class],
    version = 1,
)
abstract class TPlayerDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
}
