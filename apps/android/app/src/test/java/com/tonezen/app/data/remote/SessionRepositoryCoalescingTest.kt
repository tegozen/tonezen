package com.tonezen.app.data.remote

import com.tonezen.app.data.local.SecureSessionStore
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.domain.session.SessionManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class SessionRepositoryCoalescingTest {
    private val sessionStore = mockk<SecureSessionStore>(relaxed = true)
    private val authRepository = mockk<AuthRepository>()
    private val networkMonitor = mockk<NetworkMonitor>()
    private val sessionManager = SessionManager(refreshLeadSeconds = 300, clock = { 2000 })

    private lateinit var repository: SessionRepository

    private val staleSession = StoredSession(
        userId = "u1",
        email = "user@example.com",
        displayName = "User",
        accessToken = "access-old",
        refreshToken = "refresh",
        expiresAtEpochSeconds = 1000,
        memberSinceEpochMs = null,
        avatarUrl = null,
    )

    private val freshSession = staleSession.copy(
        accessToken = "access-new",
        expiresAtEpochSeconds = 4000,
    )

    @Before
    fun setUp() {
        every { sessionStore.load() } returns staleSession
        every { networkMonitor.isOnline() } returns true
        coEvery { authRepository.refreshSession("refresh") } coAnswers {
            delay(50)
            freshSession
        }
        repository = SessionRepository(
            sessionStore = sessionStore,
            authRepository = authRepository,
            networkMonitor = networkMonitor,
            sessionManager = sessionManager,
        )
    }

    @Test
    fun refreshIfNeeded_coalescesConcurrentRefreshCalls() = runTest {
        val first = async { repository.refreshIfNeeded(staleSession) }
        val second = async { repository.refreshIfNeeded(staleSession) }

        assertEquals("access-new", first.await()?.accessToken)
        assertEquals("access-new", second.await()?.accessToken)
        coVerify(exactly = 1) { authRepository.refreshSession("refresh") }
    }

    @Test
    fun refreshIfNeeded_waitsForInFlightRefreshBeforeSkipping() = runTest {
        val refresh = async { repository.refreshIfNeeded(staleSession) }
        delay(10)
        val waiter = async { repository.refreshIfNeeded(freshSession) }

        assertEquals("access-new", refresh.await()?.accessToken)
        assertEquals("access-new", waiter.await()?.accessToken)
        coVerify(exactly = 1) { authRepository.refreshSession("refresh") }
    }

    @Test
    fun refreshIfNeeded_preservesAvatarUrlWhenMetadataOmitsIt() = runTest {
        val avatarUrl = "https://tonezen.tegozen.ru/storage/v1/object/public/avatars/u1/avatar.jpg"
        val sessionWithAvatar = staleSession.copy(avatarUrl = avatarUrl)
        every { sessionStore.load() } returns sessionWithAvatar
        coEvery { authRepository.refreshSession("refresh") } returns freshSession
        repository.saveSession(sessionWithAvatar)

        val result = repository.refreshIfNeeded(sessionWithAvatar)

        assertEquals(avatarUrl, result?.avatarUrl)
    }
}
