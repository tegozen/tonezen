package com.tonezen.app.data.remote.progress

import com.tonezen.app.data.remote.getRemoteJson
import com.tonezen.app.data.remote.RemoteHttpException
import com.tonezen.app.domain.model.AudiobookProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ProgressCasConflictException(
    val remote: RemoteProgress?,
) : Exception("Progress CAS conflict")

class ProgressRemoteApi(
    private val apiRoot: String,
    private val httpClient: OkHttpClient,
) {
    suspend fun pushProgress(
        accessToken: String,
        bookId: String,
        progress: AudiobookProgress,
        baseRevision: Long,
    ): RemoteProgress =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("track_id", progress.trackId)
                .put("position_ms", progress.positionMs)
                .put("base_revision", baseRevision)
                .toString()
            val request = Request.Builder()
                .url("$apiRoot/progress/audiobooks/$bookId")
                .put(body.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $accessToken")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val raw = response.body?.string().orEmpty()
                if (response.code == 409) {
                    val progressJson = runCatching {
                        JSONObject(raw).optJSONObject("progress")
                    }.getOrNull()
                    throw ProgressCasConflictException(progressJson?.toRemoteProgress())
                }
                if (!response.isSuccessful) {
                    throw RemoteHttpException(response.code, "Progress push failed (${response.code})")
                }
                JSONObject(raw).getJSONObject("progress").toRemoteProgress()
            }
        }

    suspend fun fetchProgress(accessToken: String): List<RemoteProgress> = withContext(Dispatchers.IO) {
        val json = getRemoteJson(httpClient, "$apiRoot/progress/audiobooks", accessToken)
        val arr = json.optJSONArray("progress") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val row = arr.getJSONObject(i)
                add(row.toRemoteProgress())
            }
        }
    }

    data class RemoteProgress(
        val bookId: String,
        val trackId: String,
        val positionMs: Long,
        val updatedAt: String,
        val revision: Long,
    )

    private fun JSONObject.toRemoteProgress() = RemoteProgress(
        bookId = getString("book_id"),
        trackId = getString("track_id"),
        positionMs = getLong("position_ms"),
        updatedAt = getString("updated_at"),
        revision = optLong("revision", 0L),
    )
}
