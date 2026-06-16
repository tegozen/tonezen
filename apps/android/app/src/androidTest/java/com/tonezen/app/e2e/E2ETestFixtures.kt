package com.tonezen.app.e2e

import com.tonezen.app.domain.model.StoredSession

fun testOfflineSession(): StoredSession {
    val expiresAt = System.currentTimeMillis() / 1000 + 86_400 * 365
    return StoredSession(
        userId = "e2e-user",
        email = "e2e@tonezen.test",
        displayName = "E2E User",
        accessToken = "e2e-access-token",
        refreshToken = "e2e-refresh-token",
        expiresAtEpochSeconds = expiresAt,
        memberSinceEpochMs = 1_700_000_000_000L,
    )
}
