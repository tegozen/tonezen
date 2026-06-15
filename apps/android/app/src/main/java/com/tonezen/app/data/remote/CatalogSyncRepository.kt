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

    private val _catalogUpdated = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val catalogUpdated: SharedFlow<Unit> = _catalogUpdated.asSharedFlow()

    private var activeUserId: String? = null
    private var debounceJob: Job? = null
    private var syncInFlight = false

    fun start(session: StoredSession) {
        if (activeUserId == session.userId) return
        stop()
        activeUserId = session.userId
        realtimeClient.connect(session.userId, session.accessToken)
    }

    fun stop() {
        debounceJob?.cancel()
        debounceJob = null
        activeUserId = null
        realtimeClient.disconnect()
    }

    fun updateAuth(accessToken: String) {
        val userId = activeUserId ?: return
        realtimeClient.connect(userId, accessToken)
    }

    private fun scheduleSync() {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(SYNC_DEBOUNCE_MS)
            if (!networkMonitor.isOnline()) return@launch
            val token = sessionRepository.loadSession()?.accessToken ?: return@launch
            if (syncInFlight) return@launch
            syncInFlight = true
            try {
                catalogRepository.syncFromRemote(token)
                _catalogUpdated.emit(Unit)
            } finally {
                syncInFlight = false
            }
        }
    }

    private companion object {
        const val SYNC_DEBOUNCE_MS = 2_000L
    }
}
