package com.tonezen.app.data.remote

import com.tonezen.app.BuildConfig
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.domain.model.StoredSession
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@Singleton
class ProfileSyncRepository @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val userProfileMirrorRepository: UserProfileMirrorRepository,
    private val networkMonitor: NetworkMonitor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeClient = RealtimeProfileClient(
        supabaseUrl = BuildConfig.BASE_URL,
        anonKey = BuildConfig.SUPABASE_ANON_KEY,
        scope = scope,
        onProfileChange = { row -> applyRemote(row) },
    )
    private val binder = RealtimeSessionBinder(
        sessionRepository = sessionRepository,
        networkMonitor = networkMonitor,
        scope = scope,
        connect = { session ->
            realtimeClient.connect(
                userId = session.userId,
                accessToken = session.accessToken,
                onAuthError = ::scheduleAuthRecovery,
            )
        },
        disconnect = realtimeClient::disconnect,
    )

    fun start(session: StoredSession) {
        scope.launch {
            binder.ensureStarted(session)
        }
    }

    fun stop() {
        binder.stop()
    }

    suspend fun updateAuth() {
        val session = sessionRepository.loadSession() ?: return
        if (binder.currentUserId != null && session.userId != binder.currentUserId) return
        binder.ensureConnected(session)
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

    private fun scheduleAuthRecovery() {
        binder.scheduleAuthRecovery()
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
