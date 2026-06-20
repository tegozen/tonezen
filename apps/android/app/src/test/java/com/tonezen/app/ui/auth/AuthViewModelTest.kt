package com.tonezen.app.ui.auth

import com.tonezen.app.data.remote.AuthRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.ui.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AuthViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun verifyInviteCode_setsVerifiedState() = runTest {
        val sessionRepository = mockk<SessionRepository>(relaxed = true)
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.verifyInviteCode("CODE12345678") } returns true
        val viewModel = AuthViewModel(sessionRepository, authRepository)

        viewModel.verifyInviteCode("CODE12345678")

        assertTrue(viewModel.uiState.value.inviteCodeVerified)
    }

    @Test
    fun registerWithInvite_createsAccountThenSavesLoginSession() = runTest {
        val session = StoredSession(
            userId = "user-1",
            email = "user@example.com",
            displayName = "User",
            accessToken = "at",
            refreshToken = "rt",
            expiresAtEpochSeconds = 123,
        )
        val sessionRepository = mockk<SessionRepository>()
        val authRepository = mockk<AuthRepository>()
        coEvery {
            authRepository.signUpWithInvite(
                inviteCode = "CODE12345678",
                email = "user@example.com",
                password = "secret123",
                displayName = "User",
            )
        } just runs
        coEvery { authRepository.signInWithPassword("user@example.com", "secret123") } returns session
        every { sessionRepository.saveSession(session) } just runs
        val viewModel = AuthViewModel(sessionRepository, authRepository)

        viewModel.registerWithInvite(
            inviteCode = "CODE12345678",
            email = "user@example.com",
            displayName = "User",
            password = "secret123",
            confirmPassword = "secret123",
        )

        verify { sessionRepository.saveSession(session) }
    }

    @Test
    fun registerWithInvite_rejectsPasswordMismatch() = runTest {
        val sessionRepository = mockk<SessionRepository>(relaxed = true)
        val authRepository = mockk<AuthRepository>(relaxed = true)
        val viewModel = AuthViewModel(sessionRepository, authRepository)

        viewModel.registerWithInvite(
            inviteCode = "CODE12345678",
            email = "user@example.com",
            displayName = "User",
            password = "secret123",
            confirmPassword = "different",
        )

        assertEquals(AuthViewModel.AUTH_PASSWORD_MISMATCH_ERROR, viewModel.uiState.value.error)
    }

    @Test
    fun requestPasswordRecovery_setsGenericSentState() = runTest {
        val sessionRepository = mockk<SessionRepository>(relaxed = true)
        val authRepository = mockk<AuthRepository>()
        coEvery { authRepository.requestPasswordRecovery("user@example.com") } just runs
        val viewModel = AuthViewModel(sessionRepository, authRepository)

        viewModel.requestPasswordRecovery("user@example.com")

        assertTrue(viewModel.uiState.value.passwordRecoverySent)
    }
}
