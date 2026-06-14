package com.tonezen.app.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRefreshFailureTest {
    @Test
    fun invalidRefreshTokenStatuses_areAuthFailures() {
        listOf(400, 401, 403).forEach { code ->
            val error = RemoteHttpException(code, "refresh failed")
            assertTrue(error.isInvalidRefreshToken)
        }
    }

    @Test
    fun transientServerErrors_areNotInvalidRefreshToken() {
        listOf(408, 429, 500, 503).forEach { code ->
            val error = RemoteHttpException(code, "server error")
            assertFalse(error.isInvalidRefreshToken)
        }
    }
}
