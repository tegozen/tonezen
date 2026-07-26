package com.tonezen.app.data.local

import android.content.Context
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicLibraryResolver
import com.tonezen.app.domain.music.MusicLibraryTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CatalogTracksRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogDao: CatalogDao,
    private val booksRepository: CatalogBooksRepository,
) {
    suspend fun canonicalBookIdForTrack(trackId: String): String? = withContext(Dispatchers.IO) {
        catalogDao.getBookIdForTrack(trackId)
    }

    suspend fun findTrackInCatalog(trackId: String): Track? = withContext(Dispatchers.IO) {
        catalogDao.getTrackById(trackId)?.toSanitizedDomain(context, includeWaveformPeaks = true)
    }

    suspend fun findBookForTrack(trackId: String): Book? {
        val bookId = canonicalBookIdForTrack(trackId) ?: return null
        return booksRepository.getBook(bookId)
    }

    suspend fun getAllTracksByBookId(limit: Int? = null): Map<String, List<Track>> {
        val entities = if (limit != null) {
            catalogDao.getAllTracksLimited(limit)
        } else {
            catalogDao.getAllTracks()
        }
        return entities.map { it.toSanitizedDomain(context, includeWaveformPeaks = false) }
            .groupBy { it.bookId }
    }

    suspend fun getTracksByBookIds(bookIds: Collection<String>): Map<String, List<Track>> {
        if (bookIds.isEmpty()) return emptyMap()
        return catalogDao.getTracksForBooks(bookIds.distinct())
            .map { it.toSanitizedDomain(context, includeWaveformPeaks = false) }
            .groupBy { it.bookId }
    }

    suspend fun getTracksForBook(bookId: String): List<Track> =
        catalogDao.getTracksForBook(bookId).map {
            it.toSanitizedDomain(context, includeWaveformPeaks = false)
        }

    suspend fun getTracksOrderedByDownloadedAt(limit: Int): List<Track> =
        catalogDao.getTracksOrderedByDownloadedAt(limit).map {
            it.toSanitizedDomain(context, includeWaveformPeaks = false)
        }

    suspend fun resolveMusicLibraryTracks(): List<MusicLibraryTrack> {
        val allBooks = booksRepository.getAllBooks()
        val musicTracks = catalogDao.getMusicTracks()
            .map { it.toSanitizedDomain(context, includeWaveformPeaks = false) }
            .groupBy { it.bookId }
        return MusicLibraryResolver.resolve(allBooks) { bookId ->
            musicTracks[bookId].orEmpty()
        }
    }
}
