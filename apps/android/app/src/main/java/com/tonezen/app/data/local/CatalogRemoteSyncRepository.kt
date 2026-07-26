package com.tonezen.app.data.local

import android.content.Context
import com.tonezen.app.data.remote.catalog.CatalogRemoteApi
import com.tonezen.app.data.waveformPeaksToJson
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.normalizeAuthor
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray

@Singleton
class CatalogRemoteSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogDao: CatalogDao,
    private val catalogRemoteApi: CatalogRemoteApi,
    private val localPathsRepository: CatalogLocalPathsRepository,
) {
    private val syncLock = Mutex()
    private var syncDeferred: Deferred<List<Book>>? = null

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
        localPathsRepository.invalidateDownloadedTrackIdsCache()
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
                    localDownloadedAt = existing?.localDownloadedAt
                        ?: localPath?.let { System.currentTimeMillis() },
                    waveformPeaksJson = waveformPeaksToJson(track.waveformPeaks),
                )
            },
        )
    }

    private fun expectedTrackFile(bookId: String, trackId: String): File? =
        SafeLocalStorage.trackFile(context.filesDir, bookId, trackId)
}
