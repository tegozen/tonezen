package com.tonezen.app.data.local

import android.content.Context
import android.os.StatFs
import com.tonezen.app.domain.downloads.DownloadedBookSummary
import com.tonezen.app.domain.downloads.StorageStats
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CatalogDownloadStatsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogDao: CatalogDao,
    private val localPathsRepository: CatalogLocalPathsRepository,
) {
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

    suspend fun deleteAllDownloads() {
        val books = catalogDao.getAllBooks()
        books.forEach { book ->
            localPathsRepository.clearLocalDownloads(book.id)
            catalogDao.getTracksForBook(book.id).forEach { track ->
                track.localPath
                    ?.takeIf { SafeLocalStorage.isUnderAppFilesRoot(context.filesDir, it) }
                    ?.let { File(it).delete() }
            }
        }
        File(context.filesDir, "downloads").deleteRecursively()
        localPathsRepository.invalidateDownloadedTrackIdsCache()
    }
}
