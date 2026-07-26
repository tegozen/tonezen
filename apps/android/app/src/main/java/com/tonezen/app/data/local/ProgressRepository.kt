package com.tonezen.app.data.local

import com.tonezen.app.domain.model.AudiobookProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressRepository @Inject constructor(
    private val catalogDao: CatalogDao,
) {
    @Volatile
    var activeUserId: String? = null

    private fun requireUserId(): String =
        activeUserId ?: error("Progress active user is not set")

    suspend fun getProgress(bookId: String): AudiobookProgress? {
        val userId = activeUserId ?: return null
        return catalogDao.getProgress(userId, bookId)?.toDomain()
    }

    suspend fun getProgressEntity(bookId: String): AudiobookProgressEntity? {
        val userId = activeUserId ?: return null
        return catalogDao.getProgress(userId, bookId)
    }

    suspend fun upsertProgress(progress: AudiobookProgress, pendingSync: Boolean) {
        catalogDao.upsertProgress(progress.toEntity(requireUserId(), pendingSync))
    }

    suspend fun upsertProgressEntity(entity: AudiobookProgressEntity) {
        catalogDao.upsertProgress(entity)
    }

    suspend fun getPendingProgress(): List<AudiobookProgressEntity> {
        val userId = activeUserId ?: return emptyList()
        return catalogDao.getPendingProgress(userId)
    }

    suspend fun getAllProgress(): List<AudiobookProgress> {
        val userId = activeUserId ?: return emptyList()
        return catalogDao.getAllProgress(userId).map { it.toDomain() }
    }

    suspend fun deleteProgress(bookId: String) {
        val userId = activeUserId ?: return
        catalogDao.deleteProgress(userId, bookId)
    }

    suspend fun deleteProgressForUser(userId: String) {
        catalogDao.deleteProgressForUser(userId)
    }

    suspend fun getProgressForBooks(bookIds: Collection<String>): Map<String, AudiobookProgress?> {
        val userId = activeUserId ?: return emptyMap()
        if (bookIds.isEmpty()) return emptyMap()
        return catalogDao.getProgressForBooks(userId, bookIds.distinct())
            .associate { it.bookId to it.toDomain() }
    }

    suspend fun isProgressPendingSync(bookId: String): Boolean =
        getProgressEntity(bookId)?.pendingSync == true

    suspend fun getPendingSyncCount(): Int =
        getPendingProgress().size

    suspend fun hasAnyProgress(): Boolean {
        val userId = activeUserId ?: return false
        return catalogDao.getProgressCount(userId) > 0
    }
}
