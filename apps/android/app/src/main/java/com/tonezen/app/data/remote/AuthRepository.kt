package com.tonezen.app.data.remote

import com.tonezen.app.domain.model.StoredSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

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
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("Auth failed (${response.code}): $text")
            }
            val json = JSONObject(text)
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
}
