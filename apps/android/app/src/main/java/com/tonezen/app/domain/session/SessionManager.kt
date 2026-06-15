package com.tonezen.app.domain.session

import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.domain.model.StoredSession

class SessionManager(
    private val refreshLeadSeconds: Long = 300,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    @Volatile
    private var refreshInFlight = false

    fun resolveState(session: StoredSession?, isOnline: Boolean): SessionState {
        if (session == null) return SessionState.UNAUTHENTICATED
        if (!isOnline) return SessionState.AUTHENTICATED_OFFLINE
        return if (isExpired(session)) SessionState.AUTHENTICATED_STALE else SessionState.AUTHENTICATED_ONLINE
    }

    fun shouldRefresh(session: StoredSession?, isOnline: Boolean): Boolean {
        if (session == null || !isOnline) return false
        val now = clock()
        return now >= session.expiresAtEpochSeconds - refreshLeadSeconds
    }

    fun isExpired(session: StoredSession): Boolean = clock() >= session.expiresAtEpochSeconds

    fun isAccessTokenUsable(session: StoredSession, skewSeconds: Long = 30): Boolean =
        clock() < session.expiresAtEpochSeconds - skewSeconds

    fun beginRefresh(): Boolean {
        if (refreshInFlight) return false
        refreshInFlight = true
        return true
    }

    fun endRefresh() {
        refreshInFlight = false
    }

    fun canUseForApi(session: StoredSession?, isOnline: Boolean): Boolean {
        val state = resolveState(session, isOnline)
        return state == SessionState.AUTHENTICATED_ONLINE ||
            state == SessionState.AUTHENTICATED_STALE
    }
}
