package com.tonezen.app.data.local

import android.content.Context
import android.os.StatFs
import com.tonezen.app.data.remote.ApiClient
import com.tonezen.app.domain.downloads.DownloadedBookSummary
import com.tonezen.app.domain.downloads.StorageStats
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.normalizeAuthor
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicLibraryResolver
import com.tonezen.app.domain.music.MusicLibraryTrack
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Singleton
class CatalogRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogDao: CatalogDao,
    private val apiClient: ApiClient,
    private val progressRepository: ProgressRepository,
) {
    suspend fun getAllBooks(): List<Book> =
        catalogDao.getAllBooks().map { it.toDomain() }

    suspend fun findBookForTrack(trackId: String): Book? {
        val bookId = catalogDao.getBookIdForTrack(trackId) ?: return null
        return catalogDao.getBook(bookId)?.toDomain()
    }

    suspend fun resolveMusicLibraryTracks(): List<MusicLibraryTrack> {
        val allBooks = getAllBooks()
        val tracksByBookId = getAllTracksByBookId()
        return MusicLibraryResolver.resolve(allBooks) { bookId ->
            tracksByBookId[bookId].orEmpty()
        }
    }

    suspend fun getAllTracksByBookId(): Map<String, List<Track>> =
        catalogDao.getAllTracks()
            .map { it.toDomain() }
            .groupBy { it.bookId }

    suspend fun getTracksByBookIds(bookIds: Collection<String>): Map<String, List<Track>> {
        if (bookIds.isEmpty()) return emptyMap()
        return catalogDao.getTracksForBooks(bookIds.distinct())
            .map { it.toDomain() }
            .groupBy { it.bookId }
    }

    suspend fun getProgressByBookIds(bookIds: Collection<String>): Map<String, AudiobookProgress?> =
        progressRepository.getProgressForBooks(bookIds)

    suspend fun getDownloadedTrackIds(): Set<String> = withContext(Dispatchers.IO) {
        catalogDao.getAllTracks()
            .asSequence()
            .filter { entity ->
                val path = entity.localPath ?: return@filter false
                SafeLocalStorage.isUnderAppFilesRoot(context.filesDir, path) &&
                    File(path).isFile &&
                    File(path).length() > 0L
            }
            .map { it.id }
            .toSet()
    }

    suspend fun getTracksForBook(bookId: String): List<Track> =
        catalogDao.getTracksForBook(bookId).map { it.toDomain() }

    suspend fun getProgress(bookId: String): AudiobookProgress? =
        progressRepository.getProgress(bookId)

    suspend fun clearProgress(bookId: String) {
        progressRepository.deleteProgress(bookId)
    }

    suspend fun downloadedBookIds(books: List<Book>): Set<String> {
        val withDownloads = catalogDao.getBookIdsWithDownloads().toSet()
        return books.asSequence().map { it.id }.filter { it in withDownloads }.toSet()
    }

    suspend fun getAllCycles(): List<Cycle> = withContext(Dispatchers.IO) {
        val booksById = catalogDao.getAllBooks().associate { it.id to it.toDomain() }
        catalogDao.getAllCycles().mapNotNull { it.toDomain(booksById) }
    }

    suspend fun syncFromRemote(accessToken: String?): List<Book> = withContext(Dispatchers.IO) {
        val remoteCycles = apiClient.fetchCycles(accessToken)
        val remoteBooks = apiClient.fetchBooks(accessToken)
        catalogDao.upsertBooks(
            remoteBooks.map { book ->
                BookEntity(
                    book.id,
                    book.slug,
                    book.contentType.name.lowercase(),
                    book.title,
                    normalizeAuthor(book.author),
                    null,
                    "",
                )
            },
        )
        catalogDao.upsertCycles(
            remoteCycles.map { cycle ->
                CycleEntity(
                    id = cycle.id,
                    slug = cycle.slug,
                    title = cycle.title,
                    bookOrderJson = JSONArray(cycle.books.map { it.id }).toString(),
                )
            },
        )
        val remoteCycleIds = remoteCycles.map { it.id }
        if (remoteCycleIds.isNotEmpty()) {
            catalogDao.deleteCyclesNotIn(remoteCycleIds)
        }
        val semaphore = Semaphore(8)
        coroutineScope {
            remoteBooks.map { book ->
                async {
                    semaphore.withPermit {
                        syncBookTracks(book, accessToken)
                    }
                }
            }.awaitAll()
        }
        val remoteIds = remoteBooks.map { it.id }
        if (remoteIds.isNotEmpty()) {
            catalogDao.deleteTracksForBooksNotIn(remoteIds)
            catalogDao.deleteBooksNotIn(remoteIds)
        }
        remoteBooks
    }

    private suspend fun syncBookTracks(book: Book, accessToken: String?) {
        val existingById = catalogDao.getTracksForBook(book.id).associateBy { it.id }
        val (_, tracks) = apiClient.fetchBookDetail(book.id, accessToken)
        catalogDao.upsertTracks(
            tracks.map { track ->
                val existing = existingById[track.id]
                val localPath = existing?.localPath?.takeIf {
                    SafeLocalStorage.isUnderAppFilesRoot(context.filesDir, it) &&
                        File(it).isFile &&
                        File(it).length() > 0L
                }
                    ?: expectedTrackFile(book.id, track.id)
                        ?.takeIf { it.isFile && it.length() > 0L }
                        ?.absolutePath
                TrackEntity(
                    track.id,
                    track.bookId,
                    track.sortOrder,
                    track.title,
                    track.filename,
                    track.durationMs,
                    localPath,
                )
            },
        )
    }

    suspend fun markTrackDownloaded(bookId: String, trackId: String, localPath: String) {
        val track = catalogDao.getTracksForBook(bookId).find { it.id == trackId } ?: return
        catalogDao.upsertTracks(listOf(track.copy(localPath = localPath)))
    }

    suspend fun resolveLocalTrackPath(bookId: String, trackId: String): String? {
        val fromDb = catalogDao.getTracksForBook(bookId).find { it.id == trackId }?.localPath
        if (fromDb != null && SafeLocalStorage.isUnderAppFilesRoot(context.filesDir, fromDb)) {
            val file = File(fromDb)
            if (file.isFile && file.length() > 0L) return fromDb
        }
        val onDisk = expectedTrackFile(bookId, trackId)
        if (onDisk?.isFile == true && onDisk.length() > 0L) {
            markTrackDownloaded(bookId, trackId, onDisk.absolutePath)
            return onDisk.absolutePath
        }
        if (fromDb != null) clearTrackLocalPath(bookId, trackId)
        return null
    }

    private fun expectedTrackFile(bookId: String, trackId: String): File? =
        SafeLocalStorage.trackFile(context.filesDir, bookId, trackId)

    suspend fun clearLocalDownloads(bookId: String) {
        catalogDao.clearLocalPathsForBook(bookId)
    }

    suspend fun isProgressPendingSync(bookId: String): Boolean =
        catalogDao.getProgress(bookId)?.pendingSync == true

    suspend fun clearTrackLocalPath(bookId: String, trackId: String) {
        val track = catalogDao.getTracksForBook(bookId).find { it.id == trackId } ?: return
        catalogDao.upsertTracks(listOf(track.copy(localPath = null)))
    }

    suspend fun getDownloadedBookSummaries(): List<DownloadedBookSummary> {
        val books = catalogDao.getAllBooks()
        val tracksByBookId = catalogDao.getAllTracks().groupBy { it.bookId }
        return books.mapNotNull { entity ->
            val book = entity.toDomain()
            val tracks = tracksByBookId[book.id].orEmpty()
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
        catalogDao.getPendingProgress().size

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
