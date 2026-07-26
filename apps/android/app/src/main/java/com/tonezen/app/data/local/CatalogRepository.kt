package com.tonezen.app.data.local

import com.tonezen.app.domain.downloads.DownloadedBookSummary
import com.tonezen.app.domain.downloads.StorageStats
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicLibraryTrack
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Thin facade over domain catalog repositories for existing inject sites. */
@Singleton
class CatalogRepository @Inject constructor(
    private val booksRepository: CatalogBooksRepository,
    private val tracksRepository: CatalogTracksRepository,
    private val cyclesRepository: CatalogCyclesRepository,
    private val localPathsRepository: CatalogLocalPathsRepository,
    private val downloadStatsRepository: CatalogDownloadStatsRepository,
    private val remoteSyncRepository: CatalogRemoteSyncRepository,
    private val progressRepository: ProgressRepository,
) {
    suspend fun getAllBooks(limit: Int? = null): List<Book> = booksRepository.getAllBooks(limit)

    suspend fun loadAllBooksPaged(
        pageSize: Int,
        onPage: suspend (List<Book>) -> Unit = {},
    ): List<Book> = booksRepository.loadAllBooksPaged(pageSize, onPage)

    suspend fun canonicalBookIdForTrack(trackId: String): String? =
        tracksRepository.canonicalBookIdForTrack(trackId)

    suspend fun findTrackInCatalog(trackId: String): Track? =
        tracksRepository.findTrackInCatalog(trackId)

    suspend fun findBookForTrack(trackId: String): Book? =
        tracksRepository.findBookForTrack(trackId)

    suspend fun getBook(bookId: String): Book? = booksRepository.getBook(bookId)

    suspend fun resolveMusicLibraryTracks(): List<MusicLibraryTrack> =
        tracksRepository.resolveMusicLibraryTracks()

    suspend fun getAllTracksByBookId(limit: Int? = null): Map<String, List<Track>> =
        tracksRepository.getAllTracksByBookId(limit)

    suspend fun getTracksByBookIds(bookIds: Collection<String>): Map<String, List<Track>> =
        tracksRepository.getTracksByBookIds(bookIds)

    suspend fun getProgressByBookIds(bookIds: Collection<String>): Map<String, AudiobookProgress?> =
        progressRepository.getProgressForBooks(bookIds)

    suspend fun getDownloadedTrackIds(): Set<String> = localPathsRepository.getDownloadedTrackIds()

    suspend fun getDownloadedTrackIdsFromCatalog(): Set<String> =
        localPathsRepository.getDownloadedTrackIdsFromCatalog()

    suspend fun reconcileLocalDownloadPaths() = localPathsRepository.reconcileLocalDownloadPaths()

    suspend fun getTracksForBook(bookId: String): List<Track> =
        tracksRepository.getTracksForBook(bookId)

    suspend fun getProgress(bookId: String): AudiobookProgress? =
        progressRepository.getProgress(bookId)

    suspend fun clearProgress(bookId: String) {
        progressRepository.deleteProgress(bookId)
    }

    suspend fun downloadedBookIds(books: List<Book>): Set<String> =
        booksRepository.downloadedBookIds(books)

    suspend fun getAllCycles(booksById: Map<String, Book>? = null): List<Cycle> =
        cyclesRepository.getAllCycles(booksById)

    suspend fun syncFromRemote(accessToken: String?): List<Book> =
        remoteSyncRepository.syncFromRemote(accessToken)

    suspend fun markTrackDownloaded(bookId: String, trackId: String, localPath: String): Boolean =
        localPathsRepository.markTrackDownloaded(bookId, trackId, localPath)

    suspend fun getTracksOrderedByDownloadedAt(limit: Int): List<Track> =
        tracksRepository.getTracksOrderedByDownloadedAt(limit)

    suspend fun resolveLocalTrackPath(bookId: String, trackId: String): String? =
        localPathsRepository.resolveLocalTrackPath(bookId, trackId)

    suspend fun clearLocalDownloads(bookId: String) {
        localPathsRepository.clearLocalDownloads(bookId)
    }

    suspend fun isProgressPendingSync(bookId: String): Boolean =
        progressRepository.isProgressPendingSync(bookId)

    suspend fun clearTrackLocalPath(bookId: String, trackId: String) {
        localPathsRepository.clearTrackLocalPath(bookId, trackId)
    }

    suspend fun getDownloadedBookSummaries(): List<DownloadedBookSummary> =
        downloadStatsRepository.getDownloadedBookSummaries()

    suspend fun getStorageStats(): StorageStats = downloadStatsRepository.getStorageStats()

    suspend fun getPendingSyncCount(): Int = progressRepository.getPendingSyncCount()

    suspend fun deleteAllDownloads() {
        downloadStatsRepository.deleteAllDownloads()
    }

    fun observeLibraryRefresh(): Flow<Unit> = flow {
        emit(Unit)
    }
}
