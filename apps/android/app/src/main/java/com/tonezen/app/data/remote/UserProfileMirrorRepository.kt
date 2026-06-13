package com.tonezen.app.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

class UserProfileMirrorRepository(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    suspend fun upsert(
        accessToken: String,
        userId: String,
        displayName: String,
        avatarUrl: String?,
        updatedAt: String,
    ): Unit = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("user_id", userId)
            .put("display_name", displayName)
            .put("avatar_url", avatarUrl?.substringBefore("?"))
            .put("updated_at", updatedAt)
            .toString()
        val url = "${supabaseUrl.trimEnd('/')}/rest/v1/user_profiles?on_conflict=user_id"
        val request = Request.Builder()
            .url(url)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $accessToken")
            .header("Prefer", "resolution=merge-duplicates,return=minimal")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RemoteHttpException(response.code, "Profile mirror upsert failed (${response.code})")
            }
        }
    }
}
