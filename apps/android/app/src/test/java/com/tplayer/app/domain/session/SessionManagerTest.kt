package com.tplayer.app.domain.session

import com.tplayer.app.domain.model.AudiobookProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import com.tplayer.app.domain.model.StoredSession

class SessionManagerTest {
    private val session = StoredSession(
        userId = "u1",
        accessToken = "access",
        refreshToken = "refresh",
        expiresAtEpochSeconds = 1000,
    )

    @Test
    fun expiredJwtOfflineRemainsAuthenticated() {
        val manager = SessionManager(clock = { 2000 })
        assertEquals(
            com.tplayer.app.domain.model.SessionState.AUTHENTICATED_OFFLINE,
            manager.resolveState(session, isOnline = false),
        )
        assertFalse(manager.shouldRefresh(session, isOnline = false))
    }

    @Test
    fun expiredJwtOnlineIsStale() {
        val manager = SessionManager(clock = { 2000 })
        assertEquals(
            com.tplayer.app.domain.model.SessionState.AUTHENTICATED_STALE,
            manager.resolveState(session, isOnline = true),
        )
        assertTrue(manager.shouldRefresh(session, isOnline = true))
    }

    @Test
    fun validJwtOnlineIsAuthenticated() {
        val manager = SessionManager(clock = { 500 })
        assertEquals(
            com.tplayer.app.domain.model.SessionState.AUTHENTICATED_ONLINE,
            manager.resolveState(session, isOnline = true),
        )
    }

    @Test
    fun mergeProgressPrefersNewerTimestamp() {
        val local = AudiobookProgress("b1", "t1", 100, 1000)
        val remote = AudiobookProgress("b1", "t2", 200, 2000)
        assertEquals(remote, ProgressMerge.mergeLww(local, remote))
    }
}
