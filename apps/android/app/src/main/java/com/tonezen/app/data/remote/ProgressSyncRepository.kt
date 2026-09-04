package com.tonezen.app.data.remote

import com.tonezen.app.BuildConfig
import com.tonezen.app.data.local.AudiobookProgressEntity
import com.tonezen.app.data.local.ProgressRepository
import com.tonezen.app.data.local.toDomain
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
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@Singleton
class ProgressSyncRepository @Inject internal constructor(
    private val progressRemoteApi: ProgressRemoteApi,
    private val progressRepository: ProgressRepository,
    private val sessionRepository: SessionRepository,
    private val networkMonitor: NetworkMonitor,
    private val hydrationState: ProgressHydrationState,
    private val reconciler: ProgressStateReconciler,
    httpClient: OkHttpClient,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
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
            reconciler.applyRemote(row.toProgressEntity(userId))
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

    val updates: SharedFlow<AudiobookProgress> = reconciler.updates
    val lastSyncAtEpochMs: StateFlow<Long?> = reconciler.lastSyncAtEpochMs

    fun bindUser(session: StoredSession?) {
        progressRepository.activeUserId = session?.userId
        if (session != null) hydrationState.bindUser(session.userId)
    }

    fun start(session: StoredSession) {
        scope.launch {
            val previous = binder.currentUserId
            if (previous != null && previous != session.userId) {
                progressRepository.deleteProgressForUser(previous)
                hydrationState.clear()
            }
            bindUser(session)
            val refreshed = binder.ensureStarted(session) ?: return@launch
            syncBestEffort(refreshed.accessToken)
        }
    }

    fun stop() {
        val previous = progressRepository.activeUserId
        binder.stop()
        hydrationState.clear()
        if (previous != null) scope.launch { progressRepository.deleteProgressForUser(previous) }
        progressRepository.activeUserId = null
    }

    suspend fun prepareHydrateFromLocalCache() {
        hydrationState.prepareFromLocalCache(progressRepository.hasAnyProgress())
    }

    suspend fun updateAuth() {
        val session = sessionRepository.loadSession() ?: return
        if (binder.currentUserId != null && session.userId != binder.currentUserId) return
        binder.ensureConnected(session)
    }

    suspend fun pullAll(accessToken: String): Boolean {
        if (!networkMonitor.isOnline()) return false
        val userId = progressRepository.activeUserId ?: return false
        val preferRemote = hydrationState.shouldPreferRemote && !hydrationState.isServerHydrated
        return try {
            progressRemoteApi.fetchProgress(accessToken).forEach { row ->
                reconciler.applyRemote(row.toProgressEntity(userId), preferRemote)
            }
            hydrationState.markHydrated(userId)
            reconciler.markSynced()
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun saveLocal(progress: AudiobookProgress, pendingSync: Boolean, accessToken: String?) {
        val entity = reconciler.saveLocal(progress, pendingSync) ?: return
        if (hydrationState.isServerHydrated && accessToken != null && networkMonitor.isOnline()) {
            tryPush(accessToken, entity)
        }
    }

    suspend fun chooseLocalProgress(bookId: String, accessToken: String?): AudiobookProgress? {
        val choice = reconciler.chooseLocal(bookId) ?: return null
        if (
            choice.shouldPush && hydrationState.isServerHydrated &&
            accessToken != null && networkMonitor.isOnline()
        ) {
            tryPush(accessToken, choice.entity)
        }
        return choice.entity.toDomain()
    }

    suspend fun chooseServerProgress(bookId: String): AudiobookProgress? = reconciler.chooseServer(bookId)

    suspend fun pushProgress(accessToken: String, entity: AudiobookProgressEntity) {
        if (hydrationState.isServerHydrated) reconciler.push(accessToken, entity)
    }

    suspend fun flushPending(accessToken: String) {
        if (!hydrationState.isServerHydrated || !networkMonitor.isOnline()) return
        for (entity in progressRepository.getPendingProgress()) {
            val repaired = reconciler.repairStuckRevision(entity)
            if (!ProgressMerger.canAutoFlush(repaired.toDomain())) continue
            tryPush(accessToken, repaired)
        }
        val hasFlushablePending = progressRepository.getPendingProgress().any {
            ProgressMerger.canAutoFlush(reconciler.repairStuckRevision(it).toDomain())
        }
        if (!hasFlushablePending) reconciler.markSynced()
    }

    private suspend fun syncBestEffort(accessToken: String) {
        pullAll(accessToken)
        flushPending(accessToken)
    }

    private fun scheduleAuthRecovery() {
        binder.scheduleAuthRecovery()
    }

    private suspend fun tryPush(accessToken: String, entity: AudiobookProgressEntity) {
        try {
            reconciler.push(accessToken, entity)
        } catch (_: Exception) {
            // Keep pending until a later flush.
        }
    }

    companion object {
        const val SPLASH_PULL_TIMEOUT_MS = 4_000L
    }
}
