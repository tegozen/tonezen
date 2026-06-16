package com.tonezen.app.data.remote

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class AuthRepositoryTest {
    @Test
    fun updateUser_stripsAvatarCacheBustFromMetadata() = runTest {
        val capturedRequest = slot<Request>()
        val response = Response.Builder()
            .request(Request.Builder().url("http://localhost:8000/auth/v1/user").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(
                """
                {
                  "id": "user-1",
                  "email": "user@example.com",
                  "created_at": "2026-06-12T00:00:00.000Z",
                  "user_metadata": {
                    "full_name": "User",
                    "avatar_url": "http://localhost:8000/storage/v1/object/public/avatars/user-1/avatar.jpg"
                  }
                }
                """.trimIndent().toResponseBody("application/json".toMediaType()),
            )
            .build()
        val call = mockk<Call>()
        every { call.execute() } returns response
        val httpClient = mockk<OkHttpClient>()
        every { httpClient.newCall(capture(capturedRequest)) } returns call

        val repository = AuthRepository(
            supabaseUrl = "http://localhost:8000",
            anonKey = "anon",
            httpClient = httpClient,
        )

        repository.updateUser(
            accessToken = "token",
            avatarUrl = "http://localhost:8000/storage/v1/object/public/avatars/user-1/avatar.jpg?v=123",
        )

        val buffer = Buffer()
        checkNotNull(capturedRequest.captured.body).writeTo(buffer)
        val body = JSONObject(buffer.readUtf8())
        assertEquals(
            "http://localhost:8000/storage/v1/object/public/avatars/user-1/avatar.jpg",
            body.getJSONObject("data").getString("avatar_url"),
        )
    }
}
