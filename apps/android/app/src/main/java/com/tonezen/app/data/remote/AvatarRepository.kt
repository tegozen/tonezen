package com.tonezen.app.data.remote

import com.tonezen.app.data.local.SafeLocalStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AvatarRepository(
    private val supabaseUrl: String,
    private val anonKey: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    suspend fun uploadAvatar(
        accessToken: String,
        userId: String,
        jpegBytes: ByteArray,
    ): String = withContext(Dispatchers.IO) {
        require(SafeLocalStorage.isSafeId(userId)) { "Invalid avatar user id" }
        val objectPath = "$userId/$AVATAR_FILE_NAME"
        val url = "${supabaseUrl.trimEnd('/')}/storage/v1/object/avatars/$objectPath"
        val request = Request.Builder()
            .url(url)
            .post(jpegBytes.toRequestBody("image/jpeg".toMediaType()))
            .header("apikey", anonKey)
            .header("Authorization", "Bearer $accessToken")
            .header("x-upsert", "true")
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw RemoteHttpException(response.code, "Avatar upload failed (${response.code})")
            }
        }
        publicAvatarUrl(userId, System.currentTimeMillis())
    }

    fun publicAvatarUrl(userId: String, cacheBustMs: Long? = null): String {
        require(SafeLocalStorage.isSafeId(userId)) { "Invalid avatar user id" }
        val base = "${supabaseUrl.trimEnd('/')}/storage/v1/object/public/avatars/$userId/$AVATAR_FILE_NAME"
        return if (cacheBustMs != null) "$base?v=$cacheBustMs" else base
    }

    companion object {
        private const val AVATAR_FILE_NAME = "avatar.jpg"
    }
}
