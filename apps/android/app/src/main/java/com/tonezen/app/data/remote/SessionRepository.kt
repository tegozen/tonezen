package com.tonezen.app.data.remote

import com.tonezen.app.data.local.SecureSessionStore
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.domain.session.SessionManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class SessionRepository @Inject constructor(
    private val sessionStore: SecureSessionStore,
    private val authRepository: AuthRepository,
    private val networkMonitor: NetworkMonitor,
    private val sessionManager: SessionManager,
) {
    private val refreshLock = Mutex()
    private var refreshInFlight: Deferred<StoredSession?>? = null
    private val _session = MutableStateFlow(sessionStore.load())
    val session: StateFlow<StoredSession?> = _session.asStateFlow()

    fun loadSession(): StoredSession? = _session.value

    fun isAccessTokenUsable(session: StoredSession? = loadSession()): Boolean {
        if (session == null) return false
        return sessionManager.isAccessTokenUsable(session)
    }

    fun saveSession(session: StoredSession) {
        sessionStore.save(session)
        _session.value = session
    }

    fun clearSession() {
        sessionStore.clear()
        _session.value = null
    }

    fun resolveState(session: StoredSession?): SessionState =
        sessionManager.resolveState(session, networkMonitor.isOnline())

    suspend fun refreshIfNeeded(session: StoredSession?): StoredSession? {
        if (session == null) return null
        return coalescedRefresh(force = false)
    }

    suspend fun enrichProfileMetadataIfMissing(session: StoredSession?): StoredSession? {
        if (session == null) return null
        if (session.memberSinceEpochMs != null) return session
        if (!networkMonitor.isOnline()) return session
        return coalescedRefresh(force = true)
    }

    private suspend fun coalescedRefresh(force: Boolean): StoredSession? {
        refreshInFlight?.takeIf { it.isActive }?.let { return it.await() }

        val session = loadSession() ?: return null
        if (!needsRefresh(session, force)) return session

        return coroutineScope {
            val deferred = refreshLock.withLock {
                refreshInFlight?.takeIf { it.isActive } ?: async(Dispatchers.IO) {
                    performRefresh()
                }.also { refreshInFlight = it }
            }
            try {
                deferred.await()
            } finally {
                refreshLock.withLock {
                    if (refreshInFlight == deferred && deferred.isCompleted) {
                        refreshInFlight = null
                    }
                }
            }
        }
    }

    private fun needsRefresh(session: StoredSession, force: Boolean): Boolean {
        if (force) return true
        return sessionManager.shouldRefresh(session, networkMonitor.isOnline()) ||
            (networkMonitor.isOnline() && sessionManager.isExpired(session))
    }

    private suspend fun performRefresh(): StoredSession? {
        val session = loadSession() ?: return null
        if (!networkMonitor.isOnline()) return session
        return try {
            val refreshed = authRepository.refreshSession(session.refreshToken)
            saveSession(refreshed)
            refreshed
        } catch (e: RemoteHttpException) {
            if (networkMonitor.isOnline() && e.isInvalidRefreshToken) {
                clearSession()
                null
            } else {
                session
            }
        } catch (_: Exception) {
            session
        }
    }
}
