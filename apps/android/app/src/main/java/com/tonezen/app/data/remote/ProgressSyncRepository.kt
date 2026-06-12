package com.tonezen.app.data.remote

import com.tonezen.app.BuildConfig
import com.tonezen.app.data.local.AudiobookProgressEntity
import com.tonezen.app.data.local.CatalogDao
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.domain.progress.ProgressMerger
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@Singleton
class ProgressSyncRepository @Inject constructor(
    private val apiClient: ApiClient,
    private val catalogDao: CatalogDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeClient = RealtimeProgressClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        scope = scope,
        onProgressChange = { row -> applyRemoteEntity(row.toEntity()) },
    )

    private val _updates = MutableSharedFlow<AudiobookProgressEntity>(extraBufferCapacity = 8)
    val updates: SharedFlow<AudiobookProgressEntity> = _updates.asSharedFlow()

    private var activeUserId: String? = null

    fun start(session: StoredSession) {
        if (activeUserId == session.userId) return
        stop()
        activeUserId = session.userId
        realtimeClient.connect(session.userId, session.accessToken)
        scope.launch {
            pullAll(session.accessToken)
            flushPending(session.accessToken)
        }
    }

    fun stop() {
        activeUserId = null
        realtimeClient.disconnect()
    }

    suspend fun updateAuth(accessToken: String) {
        val userId = activeUserId ?: return
        realtimeClient.connect(userId, accessToken)
    }

    suspend fun pullAll(accessToken: String) {
        for (row in apiClient.fetchProgress(accessToken)) {
            applyRemoteEntity(row.toEntity())
        }
    }

    suspend fun saveLocal(entity: AudiobookProgressEntity, accessToken: String?) {
        catalogDao.upsertProgress(entity)
        if (accessToken != null) {
            pushProgress(accessToken, entity)
        }
    }

    suspend fun pushProgress(accessToken: String, entity: AudiobookProgressEntity) {
        apiClient.pushProgress(
            accessToken,
            entity.bookId,
            AudiobookProgress(entity.bookId, entity.trackId, entity.positionMs, entity.updatedAtEpochMs),
        )
        catalogDao.upsertProgress(entity.copy(pendingSync = false))
    }

    private suspend fun flushPending(accessToken: String) {
        for (entity in catalogDao.getPendingProgress()) {
            pushProgress(accessToken, entity)
        }
    }

    private suspend fun applyRemoteEntity(remoteEntity: AudiobookProgressEntity) {
        val local = catalogDao.getProgress(remoteEntity.bookId)
        if (local?.pendingSync == true && local.updatedAtEpochMs > remoteEntity.updatedAtEpochMs) return
        val merged = ProgressMerger.merge(local, remoteEntity) ?: return
        val stored = merged.copy(pendingSync = false)
        catalogDao.upsertProgress(stored)
        _updates.emit(stored)
    }

    private fun ApiClient.RemoteProgress.toEntity() = AudiobookProgressEntity(
        bookId = bookId,
        trackId = trackId,
        positionMs = positionMs,
        updatedAtEpochMs = Instant.parse(updatedAt).toEpochMilli(),
        pendingSync = false,
    )
}
