package com.tonezen.app.data.remote

import com.tonezen.app.BuildConfig
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.domain.model.StoredSession
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

@Singleton
class CatalogSyncRepository @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val sessionRepository: SessionRepository,
    private val networkMonitor: NetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeClient = RealtimeCatalogClient(
        supabaseUrl = BuildConfig.BASE_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        scope = scope,
        onCatalogChange = { scheduleSync() },
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

    private val _catalogUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val catalogUpdated: SharedFlow<Unit> = _catalogUpdated.asSharedFlow()

    private var debounceJob: Job? = null

    fun start(session: StoredSession) {
        scope.launch {
            binder.ensureStarted(session)
        }
    }

    fun stop() {
        debounceJob?.cancel()
        debounceJob = null
        binder.stop()
    }

    suspend fun updateAuth() {
        val session = sessionRepository.loadSession() ?: return
        if (binder.currentUserId != null && session.userId != binder.currentUserId) return
        binder.ensureConnected(session)
    }

    private fun scheduleAuthRecovery() {
        binder.scheduleAuthRecovery()
    }

    private fun scheduleSync() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(SYNC_DEBOUNCE_MS)
            if (!networkMonitor.isOnline()) return@launch
            val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession()) ?: return@launch
            if (!sessionRepository.isAccessTokenUsable(session)) return@launch
            try {
                // Add rate limiting: 5 requests per minute minimum between sync attempts
                delay(60_000) // Rate limit: 1 sync every 60 seconds minimum
                val result = catalogRepository.syncFromRemote(session.accessToken)
                if (result.isNotEmpty()) { // Only emit if sync was successful and resulted in updates
                    _catalogUpdated.emit(Unit)
                }
            } catch (_: Exception) {
                // Best-effort; local cache remains authoritative offline.
            }
        }
    }

    private companion object {
        const val SYNC_DEBOUNCE_MS = 2_000L
        // Rate limiting: sync operations should be spaced out to avoid overwhelming network thread
        const val RATE_LIMIT_MS = 60_000L
    }
}
