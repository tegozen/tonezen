package com.tonezen.app.data.remote

import com.tonezen.app.domain.model.StoredSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.time.Instant

class AuthRepository(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    suspend fun signInWithPassword(email: String, password: String): StoredSession =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("email", email)
                .put("password", password)
                .toString()
            val session = tokenRequest("password", body, fallbackEmail = email)
            if (session.email.isBlank()) session.copy(email = email) else session
        }

    suspend fun refreshSession(refreshToken: String): StoredSession =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("refresh_token", refreshToken).toString()
            tokenRequest("refresh_token", body)
        }

    suspend fun updateUser(
        accessToken: String,
        displayName: String? = null,
        newPassword: String? = null,
        avatarUrl: String? = null,
    ): StoredSession = withContext(Dispatchers.IO) {
        val body = JSONObject()
        if (displayName != null || avatarUrl != null) {
            val data = JSONObject()
            if (displayName != null) {
                data.put("full_name", displayName)
            }
            if (avatarUrl != null) {
                data.put("avatar_url", avatarUrl.substringBefore("?"))
            }
            body.put("data", data)
        }
        if (newPassword != null) {
            body.put("password", newPassword)
        }
        val url = "${supabaseUrl.trimEnd('/')}/auth/v1/user"
        val request = Request.Builder()
            .url(url)
            .put(body.toString().toRequestBody("application/json".toMediaType()))
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $accessToken")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RemoteHttpException(response.code, "Profile update failed (${response.code})")
            }
            val user = JSONObject(response.body?.string().orEmpty())
            val resolvedEmail = user.optString("email", "")
            StoredSession(
                userId = user.getString("id"),
                email = resolvedEmail,
                displayName = displayNameFromUser(user, resolvedEmail),
                accessToken = "",
                refreshToken = "",
                expiresAtEpochSeconds = 0L,
                memberSinceEpochMs = memberSinceFromUser(user),
                avatarUrl = avatarUrlFromUser(user),
            )
        }
    }

    private fun tokenRequest(
        grantType: String,
        jsonBody: String,
        fallbackEmail: String = "",
    ): StoredSession {
        val url = "${supabaseUrl.trimEnd('/')}/auth/v1/token?grant_type=$grantType"
        val request = Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $anonKey")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RemoteHttpException(response.code, "Auth token request failed (${response.code})")
            }
            val json = JSONObject(response.body?.string().orEmpty())
            val expiresIn = json.getInt("expires_in")
            val user = json.getJSONObject("user")
            val resolvedEmail = user.optString("email", "").ifBlank { fallbackEmail }
            return StoredSession(
                userId = user.getString("id"),
                email = resolvedEmail,
                displayName = displayNameFromUser(user, resolvedEmail),
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + expiresIn,
                memberSinceEpochMs = memberSinceFromUser(user),
                avatarUrl = avatarUrlFromUser(user),
            )
        }
    }

    private fun displayNameFromUser(user: JSONObject, fallbackEmail: String): String {
        val meta = user.optJSONObject("user_metadata")
        val fromMeta = meta?.optString("full_name")?.takeIf { it.isNotBlank() }
            ?: meta?.optString("display_name")?.takeIf { it.isNotBlank() }
        if (fromMeta != null) return fromMeta
        val localPart = fallbackEmail.substringBefore("@").trim()
        if (localPart.isEmpty()) return ""
        return localPart.replaceFirstChar { it.uppercase() }
    }

    private fun memberSinceFromUser(user: JSONObject): Long? {
        val createdAt = user.optString("created_at").takeIf { it.isNotBlank() } ?: return null
        return runCatching { Instant.parse(createdAt).toEpochMilli() }.getOrNull()
    }

    private fun avatarUrlFromUser(user: JSONObject): String? {
        val meta = user.optJSONObject("user_metadata") ?: return null
        return meta.optString("avatar_url").takeIf { it.isNotBlank() }
            ?: meta.optString("picture").takeIf { it.isNotBlank() }
    }
}
