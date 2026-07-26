package com.tonezen.app.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

@Singleton
class CatalogLocalPathsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogDao: CatalogDao,
) {
    private val downloadedTrackIdsCacheLock = Mutex()
    private var downloadedTrackIdsCache: Set<String>? = null
    private var downloadedTrackIdsCacheGeneration: Long = 0

    suspend fun invalidateDownloadedTrackIdsCache() {
        downloadedTrackIdsCacheLock.withLock {
            downloadedTrackIdsCache = null
            downloadedTrackIdsCacheGeneration++
        }
    }

    suspend fun getDownloadedTrackIds(): Set<String> = withContext(Dispatchers.IO) {
        val cacheGenerationAtRead = downloadedTrackIdsCacheLock.withLock {
            downloadedTrackIdsCacheGeneration to downloadedTrackIdsCache
        }
        cacheGenerationAtRead.second?.let { return@withContext it }
        val onDiskTrackIds = scanDownloadedFilesOnDisk().keys.map { (_, trackId) -> trackId }
        val ids = catalogDao.getTracksWithLocalPath()
            .asSequence()
            .mapNotNull { entity ->
                SafeLocalStorage.sanitizeStoredLocalPath(context.filesDir, entity.localPath)
                    ?.let { entity.id }
            }
            .toMutableSet()
        ids.addAll(onDiskTrackIds)
        downloadedTrackIdsCacheLock.withLock {
            if (downloadedTrackIdsCacheGeneration == cacheGenerationAtRead.first) {
                downloadedTrackIdsCache = ids
            }
        }
        ids
    }

    suspend fun getDownloadedTrackIdsFromCatalog(): Set<String> = withContext(Dispatchers.IO) {
        catalogDao.getTracksWithLocalPath()
            .asSequence()
            .mapNotNull { entity ->
                SafeLocalStorage.sanitizeStoredLocalPath(context.filesDir, entity.localPath)
                    ?.let { entity.id }
            }
            .toSet()
    }

    /** Backfill DB localPath from files on disk (e.g. after mark failed or offline reopen). */
    suspend fun reconcileLocalDownloadPaths() = withContext(Dispatchers.IO) {
        val updates = mutableListOf<TrackEntity>()
        val onDiskByKey = scanDownloadedFilesOnDisk()

        for (entity in catalogDao.getTracksWithoutLocalPath()) {
            val safePath = onDiskByKey[entity.bookId to entity.id]
                ?: onDiskByKey.entries.firstOrNull { it.key.second == entity.id }?.value
                ?: SafeLocalStorage.findDownloadedTrack(context.filesDir, entity.id, entity.bookId)?.path
                ?: continue
            updates.add(entity.copy(localPath = safePath))
        }

        for (entity in catalogDao.getTracksWithLocalPath()) {
            val validPath = SafeLocalStorage.sanitizeExistingLocalPath(context.filesDir, entity.localPath)
            when {
                validPath == null -> {
                    val diskPath = onDiskByKey[entity.bookId to entity.id]
                        ?: onDiskByKey.entries.firstOrNull { it.key.second == entity.id }?.value
                        ?: SafeLocalStorage.findDownloadedTrack(context.filesDir, entity.id, entity.bookId)?.path
                    if (diskPath != null) {
                        updates.add(entity.copy(localPath = diskPath))
                    } else {
                        updates.add(entity.copy(localPath = null))
                    }
                }
                validPath != entity.localPath -> updates.add(entity.copy(localPath = validPath))
            }
        }

        if (updates.isNotEmpty()) {
            catalogDao.upsertTracks(updates)
            invalidateDownloadedTrackIdsCache()
        }
    }

    suspend fun markTrackDownloaded(bookId: String, trackId: String, localPath: String): Boolean {
        val safePath = SafeLocalStorage.sanitizeExistingLocalPath(context.filesDir, localPath) ?: return false
        val track = catalogDao.getTracksForBook(bookId).find { it.id == trackId }
            ?: catalogDao.getBookIdForTrack(trackId)?.let { resolvedBookId ->
                catalogDao.getTracksForBook(resolvedBookId).find { it.id == trackId }
            }
        if (track != null) {
            catalogDao.upsertTracks(
                listOf(
                    track.copy(
                        localPath = safePath,
                        localDownloadedAt = System.currentTimeMillis(),
                    ),
                ),
            )
            invalidateDownloadedTrackIdsCache()
            return true
        }
        if (catalogDao.getBookIdForTrack(trackId) == null) return false
        catalogDao.updateTrackLocalPathById(trackId, safePath, System.currentTimeMillis())
        invalidateDownloadedTrackIdsCache()
        return true
    }

    suspend fun resolveLocalTrackPath(bookId: String, trackId: String): String? = withContext(Dispatchers.IO) {
        resolveLocalTrackPathForBook(bookId, trackId)
            ?: findOnDiskTrackPath(trackId)?.let { (diskBookId, path) ->
                markTrackDownloaded(diskBookId, trackId, path)
                path
            }
    }

    suspend fun clearLocalDownloads(bookId: String) {
        catalogDao.clearLocalPathsForBook(bookId)
        invalidateDownloadedTrackIdsCache()
    }

    suspend fun clearTrackLocalPath(bookId: String, trackId: String) {
        val track = catalogDao.getTracksForBook(bookId).find { it.id == trackId }
            ?: catalogDao.getBookIdForTrack(trackId)?.let { resolvedBookId ->
                catalogDao.getTracksForBook(resolvedBookId).find { it.id == trackId }
            }
            ?: return
        catalogDao.upsertTracks(listOf(track.copy(localPath = null, localDownloadedAt = null)))
        invalidateDownloadedTrackIdsCache()
    }

    private suspend fun resolveLocalTrackPathForBook(bookId: String, trackId: String): String? {
        val fromDb = catalogDao.getTracksForBook(bookId).find { it.id == trackId }?.localPath
        if (fromDb != null && SafeLocalStorage.isUnderAppFilesRoot(context.filesDir, fromDb)) {
            val file = File(fromDb)
            if (file.isFile && file.length() > 0L) return fromDb
        }
        val onDisk = expectedTrackFile(bookId, trackId)
            ?: SafeLocalStorage.findDownloadedTrack(context.filesDir, trackId, bookId)?.file
        if (onDisk?.isFile == true && onDisk.length() > 0L) {
            markTrackDownloaded(bookId, trackId, onDisk.absolutePath)
            return onDisk.absolutePath
        }
        if (fromDb != null) clearTrackLocalPath(bookId, trackId)
        return null
    }

    private fun findOnDiskTrackPath(trackId: String): Pair<String, String>? =
        SafeLocalStorage.findDownloadedTrack(context.filesDir, trackId)?.let { it.bookId to it.path }

    private fun expectedTrackFile(bookId: String, trackId: String): File? =
        SafeLocalStorage.trackFile(context.filesDir, bookId, trackId)

    private fun scanDownloadedFilesOnDisk(): Map<Pair<String, String>, String> {
        val downloadsRoot = File(context.filesDir, "downloads")
        if (!downloadsRoot.isDirectory) return emptyMap()
        val result = mutableMapOf<Pair<String, String>, String>()
        downloadsRoot.listFiles()?.forEach { bookDir ->
            if (!bookDir.isDirectory) return@forEach
            val bookId = bookDir.name
            if (!SafeLocalStorage.isSafeId(bookId)) return@forEach
            bookDir.listFiles()?.forEach { file ->
                if (!file.isFile || file.length() <= 0L) return@forEach
                val trackId = file.name.removeSuffix(".mp3")
                if (trackId == file.name || !SafeLocalStorage.isSafeId(trackId)) return@forEach
                SafeLocalStorage.sanitizeExistingLocalPath(context.filesDir, file.absolutePath)?.let { safePath ->
                    result[bookId to trackId] = safePath
                }
            }
        }
        return result
    }
}
