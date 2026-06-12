package com.tonezen.app.data.remote

import com.tonezen.app.data.local.SecureSessionStore
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.domain.session.SessionManager
import javax.inject.Inject
import javax.inject.Singleton
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
    private val refreshMutex = Mutex()
    private val _session = MutableStateFlow(sessionStore.load())
    val session: StateFlow<StoredSession?> = _session.asStateFlow()

    fun loadSession(): StoredSession? = _session.value

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
        if (!sessionManager.shouldRefresh(session, networkMonitor.isOnline())) return session
        if (!sessionManager.beginRefresh()) return session
        return refreshMutex.withLock {
            try {
                if (!networkMonitor.isOnline()) return session
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
            } finally {
                sessionManager.endRefresh()
            }
        }
    }

    suspend fun enrichProfileMetadataIfMissing(session: StoredSession?): StoredSession? {
        if (session == null) return null
        if (session.memberSinceEpochMs != null) return session
        if (!networkMonitor.isOnline()) return session
        return refreshMutex.withLock {
            try {
                val refreshed = authRepository.refreshSession(session.refreshToken)
                saveSession(refreshed)
                refreshed
            } catch (_: Exception) {
                session
            }
        }
    }
}
