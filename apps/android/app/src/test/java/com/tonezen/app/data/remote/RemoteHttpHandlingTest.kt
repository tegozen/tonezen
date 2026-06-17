package com.tonezen.app.data.remote

import com.tonezen.app.data.remote.progress.ProgressRemoteApi
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RemoteHttpHandlingTest {
    @Test
    fun getRemoteJson_throwsRemoteHttpExceptionForHttpError() {
        val client = fixedStatusClient(503, """{"error":"unavailable"}""")

        val error = assertThrows(RemoteHttpException::class.java) {
            getRemoteJson(client, "https://tonezen.test/api/v1/catalog/cycles", null)
        }

        assertEquals(503, error.statusCode)
    }

    @Test
    fun pushProgress_throwsRemoteHttpExceptionForHttpError() = runTest {
        val api = ProgressRemoteApi(
            apiRoot = "https://tonezen.test/api/v1",
            httpClient = fixedStatusClient(500, """{"error":"db down"}"""),
        )

        val error = try {
            api.pushProgress(
                accessToken = "token",
                bookId = "book-1",
                progress = com.tonezen.app.domain.model.AudiobookProgress(
                    bookId = "book-1",
                    trackId = "track-1",
                    positionMs = 10_000L,
                    updatedAtEpochMs = 1_700_000_000_000L,
                ),
            )
            null
        } catch (error: RemoteHttpException) {
            error
        }

        assertEquals(500, error?.statusCode)
    }

    @Test
    fun pushProgress_returnsServerProgress() = runTest {
        val api = ProgressRemoteApi(
            apiRoot = "https://tonezen.test/api/v1",
            httpClient = fixedStatusClient(
                200,
                """
                {
                  "skipped": true,
                  "progress": {
                    "book_id": "book-1",
                    "track_id": "track-newer",
                    "position_ms": 42000,
                    "updated_at": "2024-06-01T00:00:00Z"
                  }
                }
                """.trimIndent(),
            ),
        )

        val progress = api.pushProgress(
            accessToken = "token",
            bookId = "book-1",
            progress = com.tonezen.app.domain.model.AudiobookProgress(
                bookId = "book-1",
                trackId = "track-old",
                positionMs = 10_000L,
                updatedAtEpochMs = 1_700_000_000_000L,
            ),
        )

        assertEquals("book-1", progress.bookId)
        assertEquals("track-newer", progress.trackId)
        assertEquals(42_000L, progress.positionMs)
        assertEquals("2024-06-01T00:00:00Z", progress.updatedAt)
    }

    private fun fixedStatusClient(statusCode: Int, body: String): OkHttpClient =
        OkHttpClient.Builder()
            .addInterceptor { chain ->
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(statusCode)
                    .message("test")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }
            .build()
}
