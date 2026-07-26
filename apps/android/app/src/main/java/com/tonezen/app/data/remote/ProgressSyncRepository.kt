package com.tonezen.app.data.remote

import android.content.Context
import com.tonezen.app.BuildConfig
import com.tonezen.app.data.local.AudiobookProgressEntity
import com.tonezen.app.data.local.ProgressRepository
import com.tonezen.app.data.local.toDomain
import com.tonezen.app.data.local.toEntity
import com.tonezen.app.data.local.toProgressEntity
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.progress.ProgressCasConflictException
import com.tonezen.app.data.remote.progress.ProgressRemoteApi
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.domain.progress.ProgressMerger
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@Singleton
class ProgressSyncRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val progressRemoteApi: ProgressRemoteApi,
    private val progressRepository: ProgressRepository,
    private val sessionRepository: SessionRepository,
    private val networkMonitor: NetworkMonitor,
    httpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("tonezen_progress_sync", Context.MODE_PRIVATE)
    private val realtimeClient = RealtimeProgressClient(
        supabaseUrl = BuildConfig.BASE_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        httpClient = httpClient.newBuilder()
            .readTimeout(0, java.util.concurrent.TimeUnit.MILLISECONDS)
            .pingInterval(30, java.util.concurrent.TimeUnit.SECONDS)
            .build(),
        scope = scope,
        onProgressChange = { row ->
            val userId = progressRepository.activeUserId ?: return@RealtimeProgressClient
            applyRemoteEntity(row.toProgressEntity(userId))
        },
    )
    private val binder = RealtimeSessionBinder(
        sessionRepository = sessionRepository,
        networkMonitor = networkMonitor,
        scope = scope,
        connect = { session ->
            realtimeClient.connect(
                userId = session.userId,
                accessToken = session.accessToken,
                onAuthError = ::scheduleAuthRecovery,
            )
        },
        disconnect = realtimeClient::disconnect,
    )

    private val _updates = MutableSharedFlow<AudiobookProgress>(extraBufferCapacity = 8)
    val updates: SharedFlow<AudiobookProgress> = _updates.asSharedFlow()

    private val _lastSyncAtEpochMs = MutableStateFlow<Long?>(null)
    val lastSyncAtEpochMs: StateFlow<Long?> = _lastSyncAtEpochMs.asStateFlow()

    @Volatile
    private var serverHydrated = false

    @Volatile
    private var preferRemoteOnHydrate = false

    fun bindUser(session: StoredSession?) {
        if (session == null) {
            progressRepository.activeUserId = null
            return
        }
        progressRepository.activeUserId = session.userId
        if (prefs.getString(KEY_HYDRATED_USER, null) == session.userId) {
            serverHydrated = true
        }
    }

    fun start(session: StoredSession) {
        scope.launch {
            val previous = binder.currentUserId
            if (previous != null && previous != session.userId) {
                progressRepository.deleteProgressForUser(previous)
                prefs.edit().remove(KEY_HYDRATED_USER).apply()
                serverHydrated = false
            }
            bindUser(session)
            val refreshed = binder.ensureStarted(session) ?: return@launch
            syncBestEffort(refreshed.accessToken)
        }
    }

    fun stop() {
        val previous = progressRepository.activeUserId
        binder.stop()
        serverHydrated = false
        preferRemoteOnHydrate = false
        prefs.edit().remove(KEY_HYDRATED_USER).apply()
        if (previous != null) {
            scope.launch { progressRepository.deleteProgressForUser(previous) }
        }
        progressRepository.activeUserId = null
    }

    suspend fun prepareHydrateFromLocalCache() {
        if (serverHydrated) return
        preferRemoteOnHydrate = !progressRepository.hasAnyProgress()
    }

    suspend fun updateAuth() {
        val session = sessionRepository.loadSession() ?: return
        if (binder.currentUserId != null && session.userId != binder.currentUserId) return
        binder.ensureConnected(session)
    }

    suspend fun pullAll(accessToken: String): Boolean {
        if (!networkMonitor.isOnline()) return false
        val userId = progressRepository.activeUserId ?: return false
        val preferRemote = preferRemoteOnHydrate && !serverHydrated
        return try {
            for (row in progressRemoteApi.fetchProgress(accessToken)) {
                applyRemoteEntity(row.toProgressEntity(userId), preferRemote = preferRemote)
            }
            serverHydrated = true
            preferRemoteOnHydrate = false
            prefs.edit().putString(KEY_HYDRATED_USER, userId).apply()
            markSynced()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun saveLocal(progress: AudiobookProgress, pendingSync: Boolean, accessToken: String?) {
        val userId = progressRepository.activeUserId ?: return
        val existing = progressRepository.getProgressEntity(progress.bookId)
        val withServers = progress.copy(
            revision = existing?.revision ?: existing?.serverRevision ?: progress.revision,
            serverTrackId = existing?.serverTrackId,
            serverPositionMs = existing?.serverPositionMs,
            serverRevision = existing?.serverRevision,
            conflictChoiceKey = existing?.conflictChoiceKey,
        )
        val snapshot = ProgressMerger.getServerSnapshot(withServers)
        val nextKey = if (
            snapshot != null &&
            ProgressMerger.hasConflict(withServers, snapshot) &&
            !existing?.conflictChoiceKey.isNullOrBlank()
        ) {
            // Keep auto-flush after «На устройстве» while the user keeps listening.
            ProgressMerger.conflictChoiceKey(withServers, snapshot)
        } else {
            null
        }
        val entity = withServers.copy(conflictChoiceKey = nextKey).toEntity(userId, pendingSync)
        progressRepository.upsertProgressEntity(entity)
        _updates.emit(entity.toDomain())
        if (serverHydrated && accessToken != null && networkMonitor.isOnline()) {
            try {
                pushProgress(accessToken, entity)
            } catch (_: Exception) {
                // Keep pendingSync until flush.
            }
        }
    }

    suspend fun chooseLocalProgress(bookId: String, accessToken: String?): AudiobookProgress? {
        val local = progressRepository.getProgressEntity(bookId) ?: return null
        val snapshot = ProgressMerger.getServerSnapshot(local.toDomain()) ?: return local.toDomain()
        val key = ProgressMerger.conflictChoiceKey(local.toDomain(), snapshot)
        val updated = local.copy(conflictChoiceKey = key, pendingSync = true)
        progressRepository.upsertProgressEntity(updated)
        if (serverHydrated && accessToken != null && networkMonitor.isOnline()) {
            try {
                pushProgress(accessToken, updated)
            } catch (_: Exception) {
            }
        }
        val domain = updated.toDomain()
        _updates.emit(domain)
        return domain
    }

    suspend fun chooseServerProgress(bookId: String): AudiobookProgress? {
        val local = progressRepository.getProgressEntity(bookId) ?: return null
        val snapshot = ProgressMerger.getServerSnapshot(local.toDomain()) ?: return local.toDomain()
        val key = ProgressMerger.conflictChoiceKey(local.toDomain(), snapshot)
        val applied = local.copy(
            trackId = snapshot.trackId,
            positionMs = snapshot.positionMs,
            revision = snapshot.revision,
            serverTrackId = snapshot.trackId,
            serverPositionMs = snapshot.positionMs,
            serverRevision = snapshot.revision,
            pendingSync = false,
            conflictChoiceKey = key,
        )
        progressRepository.upsertProgressEntity(applied)
        val domain = applied.toDomain()
        _updates.emit(domain)
        return domain
    }

    suspend fun pushProgress(accessToken: String, entity: AudiobookProgressEntity) {
        if (!serverHydrated) return
        if (!ProgressMerger.canAutoFlush(entity.toDomain())) return
        val baseRevision = entity.serverRevision ?: entity.revision
        try {
            val serverEntity = progressRemoteApi.pushProgress(
                accessToken,
                entity.bookId,
                entity.toDomain(),
                baseRevision,
            ).toProgressEntity(entity.userId)
            progressRepository.upsertProgressEntity(serverEntity.copy(pendingSync = false))
            _updates.emit(serverEntity.toDomain())
            markSynced()
        } catch (conflict: ProgressCasConflictException) {
            val remote = conflict.remote ?: return
            applyRemoteEntity(remote.toProgressEntity(entity.userId), preferRemote = false)
            // Snapshot refreshed — retry once if local is still auto-flushable (e.g. local ahead).
            val latest = progressRepository.getProgressEntity(entity.bookId) ?: return
            if (latest.pendingSync && ProgressMerger.canAutoFlush(latest.toDomain())) {
                try {
                    val serverEntity = progressRemoteApi.pushProgress(
                        accessToken,
                        latest.bookId,
                        latest.toDomain(),
                        latest.serverRevision ?: latest.revision,
                    ).toProgressEntity(latest.userId)
                    progressRepository.upsertProgressEntity(serverEntity.copy(pendingSync = false))
                    _updates.emit(serverEntity.toDomain())
                    markSynced()
                } catch (_: Exception) {
                    // Keep pending until a later flush.
                }
            }
        }
    }

    suspend fun flushPending(accessToken: String) {
        if (!serverHydrated || !networkMonitor.isOnline()) return
        for (entity in progressRepository.getPendingProgress()) {
            if (!ProgressMerger.canAutoFlush(entity.toDomain())) continue
            try {
                pushProgress(accessToken, entity)
            } catch (_: Exception) {
            }
        }
        if (progressRepository.getPendingProgress().none { ProgressMerger.canAutoFlush(it.toDomain()) }) {
            markSynced()
        }
    }

    private suspend fun syncBestEffort(accessToken: String) {
        pullAll(accessToken)
        flushPending(accessToken)
    }

    private fun scheduleAuthRecovery() {
        binder.scheduleAuthRecovery()
    }

    private fun markSynced() {
        _lastSyncAtEpochMs.value = System.currentTimeMillis()
    }

    private suspend fun applyRemoteEntity(
        remoteEntity: AudiobookProgressEntity,
        preferRemote: Boolean = false,
    ) {
        val local = progressRepository.getProgressEntity(remoteEntity.bookId)
        if (preferRemote || local == null) {
            progressRepository.upsertProgressEntity(remoteEntity.copy(pendingSync = false, conflictChoiceKey = null))
            _updates.emit(remoteEntity.toDomain())
            return
        }

        val prevSnapshot = ProgressMerger.getServerSnapshot(local.toDomain())
        val snapshotChanged = prevSnapshot == null ||
            prevSnapshot.trackId != remoteEntity.trackId ||
            prevSnapshot.positionMs != remoteEntity.positionMs ||
            prevSnapshot.revision != remoteEntity.revision

        val next = local.copy(
            serverTrackId = remoteEntity.trackId,
            serverPositionMs = remoteEntity.positionMs,
            serverRevision = remoteEntity.revision,
            revision = if (local.pendingSync) local.revision else remoteEntity.revision,
            conflictChoiceKey = if (snapshotChanged) null else local.conflictChoiceKey,
        )

        if (local.pendingSync && ProgressMerger.hasConflict(next.toDomain(), ProgressMerger.getServerSnapshot(next.toDomain()))) {
            progressRepository.upsertProgressEntity(next.copy(pendingSync = true))
            _updates.emit(next.copy(pendingSync = true).toDomain())
            return
        }

        if (!local.pendingSync) {
            val applied = remoteEntity.copy(
                pendingSync = false,
                conflictChoiceKey = if (snapshotChanged) null else local.conflictChoiceKey,
            )
            progressRepository.upsertProgressEntity(applied)
            _updates.emit(applied.toDomain())
            return
        }

        progressRepository.upsertProgressEntity(next.copy(pendingSync = true))
        _updates.emit(next.copy(pendingSync = true).toDomain())
    }

    companion object {
        const val SPLASH_PULL_TIMEOUT_MS = 4_000L
        private const val KEY_HYDRATED_USER = "progress_hydrated_user_id"
    }
}
