package com.tonezen.app.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.tonezen.app.domain.model.StoredSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SecureSessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val prefs by lazy {
        EncryptedSharedPreferences.create(
            appContext,
            "tonezen_session",
            MasterKey.Builder(appContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    suspend fun save(session: StoredSession) = withContext(Dispatchers.IO) {
        prefs.edit()
            .putString(KEY_USER_ID, session.userId)
            .putString(KEY_EMAIL, session.email)
            .putString(KEY_DISPLAY_NAME, session.displayName)
            .putString(KEY_ACCESS, session.accessToken)
            .putString(KEY_REFRESH, session.refreshToken)
            .putLong(KEY_EXPIRES, session.expiresAtEpochSeconds)
            .putLong(KEY_MEMBER_SINCE, session.memberSinceEpochMs ?: 0L)
            .putString(KEY_AVATAR_URL, session.avatarUrl.orEmpty())
            .apply()
    }

    suspend fun load(): StoredSession? = withContext(Dispatchers.IO) {
        val userId = prefs.getString(KEY_USER_ID, null) ?: return@withContext null
        val email = prefs.getString(KEY_EMAIL, "") ?: ""
        val storedDisplayName = prefs.getString(KEY_DISPLAY_NAME, "") ?: ""
        val displayName = storedDisplayName.ifBlank { displayNameFromEmail(email) }
        val access = prefs.getString(KEY_ACCESS, null) ?: return@withContext null
        val refresh = prefs.getString(KEY_REFRESH, null) ?: return@withContext null
        val expires = prefs.getLong(KEY_EXPIRES, 0)
        if (expires == 0L) return@withContext null
        val memberSince = prefs.getLong(KEY_MEMBER_SINCE, 0L).takeIf { it > 0L }
        val avatarUrl = prefs.getString(KEY_AVATAR_URL, "").orEmpty().takeIf { it.isNotBlank() }
        StoredSession(userId, email, displayName, access, refresh, expires, memberSince, avatarUrl)
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        prefs.edit().clear().apply()
    }

    private fun displayNameFromEmail(email: String): String {
        val localPart = email.substringBefore("@").trim()
        if (localPart.isEmpty()) return ""
        return localPart.replaceFirstChar { it.uppercase() }
    }

    companion object {
        private const val KEY_USER_ID = "user_id"
        private const val KEY_EMAIL = "email"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_ACCESS = "access_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_EXPIRES = "expires_at"
        private const val KEY_MEMBER_SINCE = "member_since"
        private const val KEY_AVATAR_URL = "avatar_url"
    }
}
