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
            tokenRequest("password", body)
        }

    suspend fun refreshSession(refreshToken: String): StoredSession =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("refresh_token", refreshToken).toString()
            tokenRequest("refresh_token", body)
        }

    private fun tokenRequest(grantType: String, jsonBody: String): StoredSession {
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
            return StoredSession(
                userId = user.getString("id"),
                accessToken = json.getString("access_token"),
                refreshToken = json.getString("refresh_token"),
                expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + expiresIn,
            )
        }
    }
}
