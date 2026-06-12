package com.tonezen.app.data.local

import android.content.Context
import android.os.StatFs
import com.tonezen.app.data.remote.ApiClient
import com.tonezen.app.domain.downloads.DownloadedBookSummary
import com.tonezen.app.domain.downloads.StorageStats
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogDao: CatalogDao,
    private val apiClient: ApiClient,
    private val progressRepository: ProgressRepository,
) {
    suspend fun getAllBooks(): List<Book> =
        catalogDao.getAllBooks().map { it.toDomain() }

    suspend fun getTracksForBook(bookId: String): List<Track> =
        catalogDao.getTracksForBook(bookId).map { it.toDomain() }

    suspend fun getProgress(bookId: String): AudiobookProgress? =
        progressRepository.getProgress(bookId)

    suspend fun downloadedBookIds(books: List<Book>): Set<String> = books
        .filter { book -> catalogDao.getTracksForBook(book.id).any { it.localPath != null } }
        .map { it.id }
        .toSet()

    suspend fun syncFromRemote(accessToken: String?): List<Book> {
        val remoteBooks = apiClient.fetchBooks(accessToken)
        catalogDao.upsertBooks(
            remoteBooks.map { book ->
                BookEntity(
                    book.id,
                    book.slug,
                    book.contentType.name.lowercase(),
                    book.title,
                    book.author,
                    null,
                    "",
                )
            },
        )
        remoteBooks.forEach { book ->
            val (_, tracks) = apiClient.fetchBookDetail(book.id, accessToken)
            catalogDao.upsertTracks(
                tracks.map { track ->
                    TrackEntity(
                        track.id,
                        track.bookId,
                        track.sortOrder,
                        track.title,
                        track.filename,
                        track.durationMs,
                        track.localPath,
                    )
                },
            )
        }
        return remoteBooks
    }

    suspend fun markTrackDownloaded(bookId: String, trackId: String, localPath: String) {
        val track = catalogDao.getTracksForBook(bookId).find { it.id == trackId } ?: return
        catalogDao.upsertTracks(listOf(track.copy(localPath = localPath)))
    }

    suspend fun clearLocalDownloads(bookId: String) {
        catalogDao.clearLocalPathsForBook(bookId)
    }

    suspend fun toggleFavorite(bookId: String) {
        val existing = catalogDao.getFavorites().any { it.bookId == bookId }
        if (existing) {
            catalogDao.deleteFavorite(bookId)
        } else {
            catalogDao.upsertFavorite(FavoriteEntity(bookId, pendingSync = true))
        }
    }

    suspend fun getFavoriteBookIds(): Set<String> =
        catalogDao.getFavorites().map { it.bookId }.toSet()

    suspend fun isFavorite(bookId: String): Boolean =
        catalogDao.getFavorites().any { it.bookId == bookId }

    suspend fun isProgressPendingSync(bookId: String): Boolean =
        catalogDao.getProgress(bookId)?.pendingSync == true

    suspend fun clearTrackLocalPath(bookId: String, trackId: String) {
        val track = catalogDao.getTracksForBook(bookId).find { it.id == trackId } ?: return
        catalogDao.upsertTracks(listOf(track.copy(localPath = null)))
    }

    suspend fun getDownloadedBookSummaries(): List<DownloadedBookSummary> {
        val books = catalogDao.getAllBooks()
        return books.mapNotNull { entity ->
            val book = entity.toDomain()
            val tracks = catalogDao.getTracksForBook(book.id)
            val downloaded = tracks.filter { it.localPath != null }
            if (downloaded.isEmpty()) return@mapNotNull null
            val sizeBytes = downloaded.sumOf { track ->
                track.localPath?.let { File(it).length() } ?: 0L
            }
            DownloadedBookSummary(
                bookId = book.id,
                title = book.title,
                author = book.author,
                contentType = book.contentType.name.lowercase(),
                downloadedTracks = downloaded.size,
                totalTracks = tracks.size,
                sizeBytes = sizeBytes,
                downloadProgress = if (downloaded.size == tracks.size) 1f else downloaded.size.toFloat() / tracks.size,
            )
        }.sortedBy { it.title.lowercase() }
    }

    suspend fun getStorageStats(): StorageStats {
        val downloadsDir = File(context.filesDir, "downloads")
        val usedBytes = downloadsDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
        val statFs = StatFs(context.filesDir.absolutePath)
        val totalBytes = statFs.totalBytes
        return StorageStats(usedBytes = usedBytes, totalBytes = totalBytes)
    }

    suspend fun getPendingSyncCount(): Int =
        catalogDao.getPendingProgress().size + catalogDao.getFavorites().count { it.pendingSync }

    suspend fun deleteAllDownloads() {
        val books = catalogDao.getAllBooks()
        books.forEach { book ->
            clearLocalDownloads(book.id)
            catalogDao.getTracksForBook(book.id).forEach { track ->
                track.localPath?.let { File(it).delete() }
            }
        }
        File(context.filesDir, "downloads").deleteRecursively()
    }

    fun observeLibraryRefresh(): Flow<Unit> = flow {
        emit(Unit)
    }
}
