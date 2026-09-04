package com.tonezen.app.data.remote

import com.tonezen.app.data.local.AudiobookProgressEntity
import com.tonezen.app.data.local.ProgressRepository
import com.tonezen.app.data.local.toDomain
import com.tonezen.app.data.local.toEntity
import com.tonezen.app.data.local.toProgressEntity
import com.tonezen.app.data.remote.progress.ProgressCasConflictException
import com.tonezen.app.data.remote.progress.ProgressRemoteApi
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.progress.ProgressMerger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
internal class ProgressStateReconciler @Inject constructor(
    private val progressRemoteApi: ProgressRemoteApi,
    private val progressRepository: ProgressRepository,
) {
    private val mutableUpdates = MutableSharedFlow<AudiobookProgress>(extraBufferCapacity = 8)
    val updates: SharedFlow<AudiobookProgress> = mutableUpdates.asSharedFlow()

    private val mutableLastSyncAtEpochMs = MutableStateFlow<Long?>(null)
    val lastSyncAtEpochMs: StateFlow<Long?> = mutableLastSyncAtEpochMs.asStateFlow()

    suspend fun saveLocal(progress: AudiobookProgress, pendingSync: Boolean): AudiobookProgressEntity? {
        val userId = progressRepository.activeUserId ?: return null
        val existing = progressRepository.getProgressEntity(progress.bookId)
        val alignedRevision = ProgressMerger.alignedClientRevision(
            playHeadRevision = existing?.revision ?: progress.revision,
            serverRevision = existing?.serverRevision,
        )
        val withServerSnapshot = progress.copy(
            revision = alignedRevision,
            serverTrackId = existing?.serverTrackId,
            serverPositionMs = existing?.serverPositionMs,
            serverRevision = existing?.serverRevision,
            conflictChoiceKey = existing?.conflictChoiceKey,
        )
        val snapshot = ProgressMerger.getServerSnapshot(withServerSnapshot)
        val nextChoiceKey = if (
            snapshot != null &&
            ProgressMerger.hasConflict(withServerSnapshot, snapshot) &&
            !existing?.conflictChoiceKey.isNullOrBlank()
        ) {
            ProgressMerger.conflictChoiceKey(withServerSnapshot, snapshot)
        } else {
            null
        }
        val entity = withServerSnapshot.copy(conflictChoiceKey = nextChoiceKey).toEntity(userId, pendingSync)
        persistAndEmit(entity)
        return entity
    }

    suspend fun chooseLocal(bookId: String): ProgressLocalChoice? {
        val local = progressRepository.getProgressEntity(bookId) ?: return null
        val snapshot = ProgressMerger.getServerSnapshot(local.toDomain())
            ?: return ProgressLocalChoice(local, shouldPush = false)
        val updated = local.copy(
            conflictChoiceKey = ProgressMerger.conflictChoiceKey(local.toDomain(), snapshot),
            pendingSync = true,
        )
        persistAndEmit(updated)
        return ProgressLocalChoice(updated, shouldPush = true)
    }

    suspend fun chooseServer(bookId: String): AudiobookProgress? {
        val local = progressRepository.getProgressEntity(bookId) ?: return null
        val snapshot = ProgressMerger.getServerSnapshot(local.toDomain()) ?: return local.toDomain()
        val applied = local.copy(
            trackId = snapshot.trackId,
            positionMs = snapshot.positionMs,
            revision = snapshot.revision,
            serverTrackId = snapshot.trackId,
            serverPositionMs = snapshot.positionMs,
            serverRevision = snapshot.revision,
            pendingSync = false,
            conflictChoiceKey = ProgressMerger.conflictChoiceKey(local.toDomain(), snapshot),
        )
        persistAndEmit(applied)
        return applied.toDomain()
    }

    suspend fun push(accessToken: String, entity: AudiobookProgressEntity) {
        val repaired = repairStuckRevision(entity)
        if (!ProgressMerger.canAutoFlush(repaired.toDomain())) return
        try {
            pushOnce(accessToken, repaired)
        } catch (conflict: ProgressCasConflictException) {
            val remote = conflict.remote ?: return
            applyRemote(remote.toProgressEntity(repaired.userId))
            retryAfterConflict(accessToken, repaired.bookId)
        }
    }

    suspend fun applyRemote(remoteEntity: AudiobookProgressEntity, preferRemote: Boolean = false) {
        val local = progressRepository.getProgressEntity(remoteEntity.bookId)
        if (preferRemote || local == null) {
            persistAndEmit(remoteEntity.copy(pendingSync = false, conflictChoiceKey = null))
            return
        }

        val previousSnapshot = ProgressMerger.getServerSnapshot(local.toDomain())
        val snapshotChanged = previousSnapshot == null ||
            previousSnapshot.trackId != remoteEntity.trackId ||
            previousSnapshot.positionMs != remoteEntity.positionMs ||
            previousSnapshot.revision != remoteEntity.revision
        val pending = local.copy(
            serverTrackId = remoteEntity.trackId,
            serverPositionMs = remoteEntity.positionMs,
            serverRevision = remoteEntity.revision,
            revision = when {
                !local.pendingSync -> remoteEntity.revision
                local.revision <= 0L -> local.serverRevision ?: remoteEntity.revision
                else -> local.revision
            },
            conflictChoiceKey = if (snapshotChanged) null else local.conflictChoiceKey,
        )
        val applied = when {
            local.pendingSync && ProgressMerger.hasConflict(
                pending.toDomain(),
                ProgressMerger.getServerSnapshot(pending.toDomain()),
            ) -> pending.copy(pendingSync = true)
            !local.pendingSync -> remoteEntity.copy(
                pendingSync = false,
                conflictChoiceKey = if (snapshotChanged) null else local.conflictChoiceKey,
            )
            else -> pending.copy(pendingSync = true)
        }
        persistAndEmit(applied)
    }

    suspend fun repairStuckRevision(entity: AudiobookProgressEntity): AudiobookProgressEntity {
        val aligned = ProgressMerger.alignedClientRevision(entity.revision, entity.serverRevision)
        if (aligned == entity.revision) return entity
        return entity.copy(revision = aligned).also { progressRepository.upsertProgressEntity(it) }
    }

    fun markSynced() {
        mutableLastSyncAtEpochMs.value = System.currentTimeMillis()
    }

    private suspend fun retryAfterConflict(accessToken: String, bookId: String) {
        val latest = progressRepository.getProgressEntity(bookId) ?: return
        val repaired = repairStuckRevision(latest)
        if (!repaired.pendingSync || !ProgressMerger.canAutoFlush(repaired.toDomain())) return
        try {
            pushOnce(accessToken, repaired)
        } catch (_: Exception) {
            // Keep pending until a later flush.
        }
    }

    private suspend fun pushOnce(accessToken: String, entity: AudiobookProgressEntity) {
        val serverEntity = progressRemoteApi.pushProgress(
            accessToken,
            entity.bookId,
            entity.toDomain(),
            entity.serverRevision ?: entity.revision,
        ).toProgressEntity(entity.userId)
        persistAndEmit(serverEntity.copy(pendingSync = false))
        markSynced()
    }

    private suspend fun persistAndEmit(entity: AudiobookProgressEntity) {
        progressRepository.upsertProgressEntity(entity)
        mutableUpdates.emit(entity.toDomain())
    }
}

internal data class ProgressLocalChoice(
    val entity: AudiobookProgressEntity,
    val shouldPush: Boolean,
)
