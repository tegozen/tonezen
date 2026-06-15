package com.tonezen.app.domain.session

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.domain.progress.ProgressMerger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionManagerTest {
    private val session = StoredSession(
        userId = "u1",
        email = "user@example.com",
        displayName = "User",
        accessToken = "access",
        refreshToken = "refresh",
        expiresAtEpochSeconds = 1000,
    )

    @Test
    fun expiredJwtOfflineRemainsAuthenticated() {
        val manager = SessionManager(clock = { 2000 })
        assertEquals(
            com.tonezen.app.domain.model.SessionState.AUTHENTICATED_OFFLINE,
            manager.resolveState(session, isOnline = false),
        )
        assertFalse(manager.shouldRefresh(session, isOnline = false))
    }

    @Test
    fun expiredJwtOnlineIsStale() {
        val manager = SessionManager(clock = { 2000 })
        assertEquals(
            com.tonezen.app.domain.model.SessionState.AUTHENTICATED_STALE,
            manager.resolveState(session, isOnline = true),
        )
        assertTrue(manager.shouldRefresh(session, isOnline = true))
        assertFalse(manager.isAccessTokenUsable(session))
    }

    @Test
    fun accessTokenUsableBeforeExpirySkew() {
        val manager = SessionManager(clock = { 900 })
        assertTrue(manager.isAccessTokenUsable(session))
    }

    @Test
    fun validJwtOnlineIsAuthenticated() {
        val manager = SessionManager(clock = { 500 })
        assertEquals(
            com.tonezen.app.domain.model.SessionState.AUTHENTICATED_ONLINE,
            manager.resolveState(session, isOnline = true),
        )
    }

    @Test
    fun mergeProgressPrefersNewerTimestamp() {
        val local = AudiobookProgress("b1", "t1", 100, 1000)
        val remote = AudiobookProgress("b1", "t2", 200, 2000)
        assertEquals(remote, ProgressMerger.merge(local, remote))
    }
}
