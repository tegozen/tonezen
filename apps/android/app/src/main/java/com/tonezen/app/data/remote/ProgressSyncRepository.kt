package com.tonezen.app.data.remote

import com.tonezen.app.BuildConfig
import com.tonezen.app.data.local.AudiobookProgressEntity
import com.tonezen.app.data.local.ProgressRepository
import com.tonezen.app.data.local.toDomain
import com.tonezen.app.data.local.toEntity
import com.tonezen.app.data.local.toProgressEntity
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.domain.progress.ProgressMerger
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
    private val progressRepository: ProgressRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeClient = RealtimeProgressClient(
        supabaseUrl = BuildConfig.BASE_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        scope = scope,
        onProgressChange = { row -> applyRemoteEntity(row.toProgressEntity()) },
    )

    private val _updates = MutableSharedFlow<AudiobookProgress>(extraBufferCapacity = 8)
    val updates: SharedFlow<AudiobookProgress> = _updates.asSharedFlow()

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
            applyRemoteEntity(row.toProgressEntity())
        }
    }

    suspend fun saveLocal(progress: AudiobookProgress, pendingSync: Boolean, accessToken: String?) {
        val entity = progress.toEntity(pendingSync)
        progressRepository.upsertProgressEntity(entity)
        if (accessToken != null) {
            pushProgress(accessToken, entity)
        }
    }

    suspend fun pushProgress(accessToken: String, entity: AudiobookProgressEntity) {
        apiClient.pushProgress(
            accessToken,
            entity.bookId,
            entity.toDomain(),
        )
        progressRepository.upsertProgressEntity(entity.copy(pendingSync = false))
    }

    private suspend fun flushPending(accessToken: String) {
        for (entity in progressRepository.getPendingProgress()) {
            pushProgress(accessToken, entity)
        }
    }

    private suspend fun applyRemoteEntity(remoteEntity: AudiobookProgressEntity) {
        val local = progressRepository.getProgressEntity(remoteEntity.bookId)
        if (local?.pendingSync == true && local.updatedAtEpochMs > remoteEntity.updatedAtEpochMs) return
        val merged = ProgressMerger.merge(local?.toDomain(), remoteEntity.toDomain()) ?: return
        val stored = merged.toEntity(pendingSync = false)
        progressRepository.upsertProgressEntity(stored)
        _updates.emit(merged)
    }
}
