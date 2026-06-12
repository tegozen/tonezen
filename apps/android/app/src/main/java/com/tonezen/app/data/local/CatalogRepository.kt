package com.tonezen.app.data.local

import com.tonezen.app.data.remote.ApiClient
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CatalogRepository @Inject constructor(
    private val catalogDao: CatalogDao,
    private val apiClient: ApiClient,
) {
    suspend fun getAllBooks(): List<Book> =
        catalogDao.getAllBooks().map { it.toDomain() }

    suspend fun getTracksForBook(bookId: String): List<Track> =
        catalogDao.getTracksForBook(bookId).map { it.toDomain() }

    suspend fun getTrackEntitiesForBook(bookId: String): List<TrackEntity> =
        catalogDao.getTracksForBook(bookId)

    suspend fun getProgress(bookId: String): AudiobookProgress? =
        catalogDao.getProgress(bookId)?.toDomain()

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

    suspend fun markTrackDownloaded(track: TrackEntity, localPath: String) {
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
}
