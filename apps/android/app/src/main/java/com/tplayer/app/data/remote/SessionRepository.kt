package com.tplayer.app.data.remote

import android.content.Context
import com.tplayer.app.domain.model.StoredSession
import com.tplayer.app.domain.model.SessionState
import com.tplayer.app.domain.session.SessionManager
import com.tplayer.app.data.local.SecureSessionStore
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SessionRepository(
    private val context: Context,
    private val sessionStore: SecureSessionStore,
    private val authRepository: AuthRepository,
    private val sessionManager: SessionManager = SessionManager(),
) {
    private val refreshMutex = Mutex()

    fun loadSession(): StoredSession? = sessionStore.load()

    fun saveSession(session: StoredSession) = sessionStore.save(session)

    fun clearSession() = sessionStore.clear()

    fun resolveState(session: StoredSession?): SessionState {
        val online = context.isNetworkAvailable()
        return sessionManager.resolveState(session, online)
    }

    suspend fun refreshIfNeeded(session: StoredSession?): StoredSession? {
        if (session == null) return null
        val online = context.isNetworkAvailable()
        if (!sessionManager.shouldRefresh(session, online)) return session
        if (!sessionManager.beginRefresh()) return session
        return refreshMutex.withLock {
            try {
                if (!context.isNetworkAvailable()) return session
                val refreshed = authRepository.refreshSession(session.refreshToken)
                sessionStore.save(refreshed)
                refreshed
            } catch (_: Exception) {
                if (context.isNetworkAvailable()) {
                    sessionStore.clear()
                    null
                } else {
                    session
                }
            } finally {
                sessionManager.endRefresh()
            }
        }
    }
}
