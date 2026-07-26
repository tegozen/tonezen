package com.tonezen.app.data.remote

import com.tonezen.app.BuildConfig
import com.tonezen.app.data.local.AudiobookProgressEntity
import com.tonezen.app.data.local.ProgressRepository
import com.tonezen.app.data.local.toDomain
import com.tonezen.app.data.local.toEntity
import com.tonezen.app.data.local.toProgressEntity
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.progress.ProgressRemoteApi
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.domain.progress.ProgressMerger
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

@Singleton
class ProgressSyncRepository @Inject constructor(
    private val progressRemoteApi: ProgressRemoteApi,
    private val progressRepository: ProgressRepository,
    private val sessionRepository: SessionRepository,
    private val networkMonitor: NetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeClient = RealtimeProgressClient(
        supabaseUrl = BuildConfig.BASE_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        scope = scope,
        onProgressChange = { row -> applyRemoteEntity(row.toProgressEntity()) },
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

    /**
     * After reinstall/login, local DB is empty. Pushing zeros with a fresh `updated_at`
     * before the first successful pull would LWW-wipe server progress. Gate HTTP push
     * until [pullAll] succeeds once for this process/session.
     */
    @Volatile
    private var serverHydrated = false

    /**
     * When the session starts with an empty local progress cache (reinstall), the first
     * successful pull must prefer server rows over any pending local zeros written during
     * fail-open UI before hydrate completed.
     */
    @Volatile
    private var preferRemoteOnHydrate = false

    fun start(session: StoredSession) {
        scope.launch {
            val refreshed = binder.ensureStarted(session) ?: return@launch
            syncBestEffort(refreshed.accessToken)
        }
    }

    fun stop() {
        binder.stop()
        serverHydrated = false
        preferRemoteOnHydrate = false
    }

    /**
     * Call before splash/login pull. Empty local cache → wipe-safe hydrate; existing local
     * progress → normal LWW (offline listening must win when back online).
     */
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
        val preferRemote = preferRemoteOnHydrate && !serverHydrated
        return try {
            for (row in progressRemoteApi.fetchProgress(accessToken)) {
                applyRemoteEntity(row.toProgressEntity(), preferRemote = preferRemote)
            }
            serverHydrated = true
            preferRemoteOnHydrate = false
            markSynced()
            true
        } catch (_: Exception) {
            // Best-effort; local cache remains authoritative offline. Do not arm push.
            false
        }
    }

    suspend fun saveLocal(progress: AudiobookProgress, pendingSync: Boolean, accessToken: String?) {
        val entity = progress.toEntity(pendingSync)
        progressRepository.upsertProgressEntity(entity)
        if (serverHydrated && accessToken != null && networkMonitor.isOnline()) {
            try {
                pushProgress(accessToken, entity)
            } catch (_: Exception) {
                // Keep pendingSync=true until a later flush succeeds.
            }
        }
    }

    suspend fun pushProgress(accessToken: String, entity: AudiobookProgressEntity) {
        if (!serverHydrated) return
        val serverEntity = progressRemoteApi.pushProgress(
            accessToken,
            entity.bookId,
            entity.toDomain(),
        ).toProgressEntity()
        val local = progressRepository.getProgressEntity(entity.bookId)
        if (local?.pendingSync == true && local.updatedAtEpochMs > serverEntity.updatedAtEpochMs) return
        progressRepository.upsertProgressEntity(serverEntity.copy(pendingSync = false))
        _updates.emit(serverEntity.toDomain())
    }

    suspend fun flushPending(accessToken: String) {
        if (!serverHydrated || !networkMonitor.isOnline()) return
        for (entity in progressRepository.getPendingProgress()) {
            try {
                pushProgress(accessToken, entity)
            } catch (_: Exception) {
                // Continue with remaining pending rows.
            }
        }
        if (progressRepository.getPendingProgress().isEmpty()) {
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
        if (!preferRemote &&
            local?.pendingSync == true &&
            local.updatedAtEpochMs > remoteEntity.updatedAtEpochMs
        ) {
            return
        }
        val merged =
            if (preferRemote) {
                remoteEntity.toDomain()
            } else {
                ProgressMerger.merge(local?.toDomain(), remoteEntity.toDomain())
            } ?: return
        val stored = merged.toEntity(pendingSync = false)
        progressRepository.upsertProgressEntity(stored)
        _updates.emit(merged)
    }

    companion object {
        /** Splash/login must fail-open quickly so offline downloads stay usable. */
        const val SPLASH_PULL_TIMEOUT_MS = 4_000L
    }
}
