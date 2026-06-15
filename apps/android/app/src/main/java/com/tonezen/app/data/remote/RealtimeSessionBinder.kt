package com.tonezen.app.data.remote

import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.domain.model.StoredSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class RealtimeSessionBinder(
    private val sessionRepository: SessionRepository,
    private val networkMonitor: NetworkMonitor,
    private val scope: CoroutineScope,
    private val connect: (StoredSession) -> Unit,
    private val disconnect: () -> Unit,
) {
    private var activeUserId: String? = null
    private var connectedAccessToken: String? = null
    private var recoveryJob: Job? = null

    val currentUserId: String? get() = activeUserId

    fun stop() {
        recoveryJob?.cancel()
        recoveryJob = null
        activeUserId = null
        connectedAccessToken = null
        disconnect()
    }

    suspend fun ensureStarted(
        session: StoredSession,
        onUserChanged: () -> Unit = {},
    ): StoredSession? {
        val refreshed = sessionRepository.refreshIfNeeded(session) ?: return null
        if (activeUserId != null && activeUserId != refreshed.userId) {
            stop()
            onUserChanged()
        }
        activeUserId = refreshed.userId
        connectIfUsable(refreshed)
        return refreshed
    }

    suspend fun ensureConnected(session: StoredSession) {
        connectIfUsable(sessionRepository.refreshIfNeeded(session) ?: return)
    }

    fun scheduleAuthRecovery() {
        if (recoveryJob?.isActive == true || !networkMonitor.isOnline()) return
        recoveryJob = scope.launch {
            delay(AUTH_RECOVERY_DELAY_MS)
            val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession()) ?: return@launch
            val userId = activeUserId ?: return@launch
            if (session.userId != userId) return@launch
            connectedAccessToken = null
            connectIfUsable(session)
        }
    }

    private fun connectIfUsable(session: StoredSession) {
        if (!sessionRepository.isAccessTokenUsable(session)) return
        if (connectedAccessToken == session.accessToken) return
        connectedAccessToken = session.accessToken
        connect(session)
    }

    private companion object {
        const val AUTH_RECOVERY_DELAY_MS = 2_000L
    }
}
