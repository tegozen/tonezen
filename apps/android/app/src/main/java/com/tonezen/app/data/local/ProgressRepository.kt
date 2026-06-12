package com.tonezen.app.data.local

import com.tonezen.app.domain.model.AudiobookProgress
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProgressRepository @Inject constructor(
    private val catalogDao: CatalogDao,
) {
    suspend fun getProgress(bookId: String): AudiobookProgress? =
        catalogDao.getProgress(bookId)?.toDomain()

    suspend fun getProgressEntity(bookId: String): AudiobookProgressEntity? =
        catalogDao.getProgress(bookId)

    suspend fun upsertProgress(progress: AudiobookProgress, pendingSync: Boolean) {
        catalogDao.upsertProgress(progress.toEntity(pendingSync))
    }

    suspend fun upsertProgressEntity(entity: AudiobookProgressEntity) {
        catalogDao.upsertProgress(entity)
    }

    suspend fun getPendingProgress(): List<AudiobookProgressEntity> =
        catalogDao.getPendingProgress()
}
