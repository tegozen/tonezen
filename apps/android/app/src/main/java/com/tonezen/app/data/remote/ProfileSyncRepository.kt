package com.tonezen.app.data.remote

import com.tonezen.app.BuildConfig
import com.tonezen.app.domain.model.StoredSession
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Singleton
class ProfileSyncRepository @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val userProfileMirrorRepository: UserProfileMirrorRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeClient = RealtimeProfileClient(
        supabaseUrl = BuildConfig.BASE_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        scope = scope,
        onProfileChange = { row -> applyRemote(row) },
    )

    private var activeUserId: String? = null

    fun start(session: StoredSession) {
        if (activeUserId == session.userId) return
        stop()
        activeUserId = session.userId
        realtimeClient.connect(session.userId, session.accessToken)
    }

    fun stop() {
        activeUserId = null
        realtimeClient.disconnect()
    }

    suspend fun updateAuth(accessToken: String) {
        val userId = activeUserId ?: return
        realtimeClient.connect(userId, accessToken)
    }

    suspend fun mirrorSession(session: StoredSession, updatedAt: String = Instant.now().toString()) {
        runCatching {
            userProfileMirrorRepository.upsert(
                accessToken = session.accessToken,
                userId = session.userId,
                displayName = session.displayName,
                avatarUrl = session.avatarUrl,
                updatedAt = updatedAt,
            )
        }
    }

    private suspend fun applyRemote(row: RealtimeProfileClient.RemoteUserProfile) {
        val session = sessionRepository.loadSession() ?: return
        if (row.userId != session.userId) return

        val avatarBase = row.avatarUrl?.substringBefore("?")
        val avatarUrl = avatarBase?.let { base ->
            val bustMs = runCatching { Instant.parse(row.updatedAt).toEpochMilli() }.getOrDefault(System.currentTimeMillis())
            "$base?v=$bustMs"
        } ?: session.avatarUrl

        sessionRepository.saveSession(
            session.copy(
                displayName = row.displayName?.takeIf { it.isNotBlank() } ?: session.displayName,
                avatarUrl = avatarUrl,
            ),
        )
    }
}
