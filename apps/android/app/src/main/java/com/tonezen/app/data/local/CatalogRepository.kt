package com.tonezen.app.data.local

import android.content.Context
import android.os.StatFs
import com.tonezen.app.data.remote.catalog.CatalogRemoteApi
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
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private val catalogRemoteApi: CatalogRemoteApi,
    private val progressRepository: ProgressRepository,
) {
    private val syncLock = Mutex()
    private var syncDeferred: Deferred<List<Book>>? = null
    private val downloadedTrackIdsCacheLock = Mutex()
    private var downloadedTrackIdsCache: Set<String>? = null

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
            .map { it.toDomainTrack() }
            .groupBy { it.bookId }

    suspend fun getTracksByBookIds(bookIds: Collection<String>): Map<String, List<Track>> {
        if (bookIds.isEmpty()) return emptyMap()
        return catalogDao.getTracksForBooks(bookIds.distinct())
            .map { it.toDomainTrack() }
            .groupBy { it.bookId }
    }

    suspend fun getProgressByBookIds(bookIds: Collection<String>): Map<String, AudiobookProgress?> =
        progressRepository.getProgressForBooks(bookIds)

    suspend fun getDownloadedTrackIds(): Set<String> = withContext(Dispatchers.IO) {
        downloadedTrackIdsCacheLock.withLock {
            downloadedTrackIdsCache?.let { return@withContext it }
        }
        val ids = catalogDao.getTracksWithLocalPath()
            .asSequence()
            .mapNotNull { entity ->
                SafeLocalStorage.sanitizeStoredLocalPath(context.filesDir, entity.localPath)
                    ?.let { entity.id }
            }
            .toSet()
        downloadedTrackIdsCacheLock.withLock {
            downloadedTrackIdsCache = ids
        }
        ids
    }

    private suspend fun invalidateDownloadedTrackIdsCache() {
        downloadedTrackIdsCacheLock.withLock {
            downloadedTrackIdsCache = null
        }
    }

    suspend fun getTracksForBook(bookId: String): List<Track> =
        catalogDao.getTracksForBook(bookId).map { it.toDomainTrack() }

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

    suspend fun syncFromRemote(accessToken: String?): List<Book> = coroutineScope {
        val deferred = syncLock.withLock {
            syncDeferred?.takeIf { it.isActive } ?: async(Dispatchers.IO) {
                performSyncFromRemote(accessToken)
            }.also { syncDeferred = it }
        }
        try {
            deferred.await()
        } finally {
            syncLock.withLock {
                if (syncDeferred == deferred && !deferred.isActive) {
                    syncDeferred = null
                }
            }
        }
    }

    private suspend fun performSyncFromRemote(accessToken: String?): List<Book> = withContext(Dispatchers.IO) {
        val remoteCycles = catalogRemoteApi.fetchCycles(accessToken)
        val remoteBooks = catalogRemoteApi.fetchBooks(accessToken)
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
        invalidateDownloadedTrackIdsCache()
        remoteBooks
    }

    private suspend fun syncBookTracks(book: Book, accessToken: String?) {
        val existingById = catalogDao.getTracksForBook(book.id).associateBy { it.id }
        val (_, tracks) = catalogRemoteApi.fetchBookDetail(book.id, accessToken)
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
                    normalizeAuthor(track.artist),
                    track.durationMs,
                    localPath,
                )
            },
        )
    }

    suspend fun markTrackDownloaded(bookId: String, trackId: String, localPath: String): Boolean {
        val safePath = SafeLocalStorage.sanitizeExistingLocalPath(context.filesDir, localPath) ?: return false
        val track = catalogDao.getTracksForBook(bookId).find { it.id == trackId } ?: return false
        catalogDao.upsertTracks(listOf(track.copy(localPath = safePath)))
        invalidateDownloadedTrackIdsCache()
        return true
    }

    suspend fun resolveLocalTrackPath(bookId: String, trackId: String): String? = withContext(Dispatchers.IO) {
        val fromDb = catalogDao.getTracksForBook(bookId).find { it.id == trackId }?.localPath
        if (fromDb != null && SafeLocalStorage.isUnderAppFilesRoot(context.filesDir, fromDb)) {
            val file = File(fromDb)
            if (file.isFile && file.length() > 0L) return@withContext fromDb
        }
        val onDisk = expectedTrackFile(bookId, trackId)
        if (onDisk?.isFile == true && onDisk.length() > 0L) {
            markTrackDownloaded(bookId, trackId, onDisk.absolutePath)
            return@withContext onDisk.absolutePath
        }
        if (fromDb != null) clearTrackLocalPath(bookId, trackId)
        null
    }

    private fun expectedTrackFile(bookId: String, trackId: String): File? =
        SafeLocalStorage.trackFile(context.filesDir, bookId, trackId)

    suspend fun clearLocalDownloads(bookId: String) {
        catalogDao.clearLocalPathsForBook(bookId)
        invalidateDownloadedTrackIdsCache()
    }

    suspend fun isProgressPendingSync(bookId: String): Boolean =
        progressRepository.isProgressPendingSync(bookId)

    suspend fun clearTrackLocalPath(bookId: String, trackId: String) {
        val track = catalogDao.getTracksForBook(bookId).find { it.id == trackId } ?: return
        catalogDao.upsertTracks(listOf(track.copy(localPath = null)))
        invalidateDownloadedTrackIdsCache()
    }

    suspend fun getDownloadedBookSummaries(): List<DownloadedBookSummary> = withContext(Dispatchers.IO) {
        val books = catalogDao.getAllBooks()
        val tracksByBookId = catalogDao.getAllTracks().groupBy { it.bookId }
        books.mapNotNull { entity ->
            val book = entity.toDomain()
            val trackEntities = tracksByBookId[book.id].orEmpty()
            var safeDownloaded = 0
            var sizeBytes = 0L
            for (trackEntity in trackEntities) {
                val path = SafeLocalStorage.sanitizeExistingLocalPath(context.filesDir, trackEntity.localPath)
                    ?: continue
                safeDownloaded++
                sizeBytes += File(path).length()
            }
            if (safeDownloaded == 0) return@mapNotNull null
            DownloadedBookSummary(
                bookId = book.id,
                title = book.title,
                author = book.author,
                contentType = book.contentType.name.lowercase(),
                downloadedTracks = safeDownloaded,
                totalTracks = trackEntities.size,
                sizeBytes = sizeBytes,
                downloadProgress = if (safeDownloaded == trackEntities.size) {
                    1f
                } else {
                    safeDownloaded.toFloat() / trackEntities.size
                },
            )
        }.sortedBy { it.title.lowercase() }
    }

    suspend fun getStorageStats(): StorageStats = withContext(Dispatchers.IO) {
        val downloadsDir = File(context.filesDir, "downloads")
        val usedBytes = downloadsDir.walkTopDown()
            .filter { it.isFile }
            .sumOf { it.length() }
        val statFs = StatFs(context.filesDir.absolutePath)
        val totalBytes = statFs.totalBytes
        StorageStats(usedBytes = usedBytes, totalBytes = totalBytes)
    }

    suspend fun getPendingSyncCount(): Int =
        progressRepository.getPendingSyncCount()

    suspend fun deleteAllDownloads() {
        val books = catalogDao.getAllBooks()
        books.forEach { book ->
            clearLocalDownloads(book.id)
            catalogDao.getTracksForBook(book.id).forEach { track ->
                track.localPath
                    ?.takeIf { SafeLocalStorage.isUnderAppFilesRoot(context.filesDir, it) }
                    ?.let { File(it).delete() }
            }
        }
        File(context.filesDir, "downloads").deleteRecursively()
    }

    fun observeLibraryRefresh(): Flow<Unit> = flow {
        emit(Unit)
    }

    private fun TrackEntity.toDomainTrack(): Track {
        val safePath = SafeLocalStorage.sanitizeStoredLocalPath(context.filesDir, localPath)
        return Track(
            id = id,
            bookId = bookId,
            sortOrder = sortOrder,
            title = title,
            filename = filename,
            artist = normalizeAuthor(artist),
            durationMs = durationMs,
            localPath = safePath,
        )
    }
}
