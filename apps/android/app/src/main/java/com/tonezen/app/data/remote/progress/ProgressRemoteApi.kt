package com.tonezen.app.data.remote.progress

import com.tonezen.app.data.remote.getRemoteJson
import com.tonezen.app.domain.model.AudiobookProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal class ProgressRemoteApi(
    private val apiRoot: String,
    private val httpClient: OkHttpClient,
) {
    suspend fun pushProgress(accessToken: String, bookId: String, progress: AudiobookProgress) =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("track_id", progress.trackId)
                .put("position_ms", progress.positionMs)
                .put("updated_at", java.time.Instant.ofEpochMilli(progress.updatedAtEpochMs).toString())
                .toString()
            val request = Request.Builder()
                .url("$apiRoot/progress/audiobooks/$bookId")
                .put(body.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $accessToken")
                .build()
            httpClient.newCall(request).execute().use { /* response handled by caller */ }
        }

    suspend fun fetchProgress(accessToken: String): List<RemoteProgress> = withContext(Dispatchers.IO) {
        val json = getRemoteJson(httpClient, "$apiRoot/progress/audiobooks", accessToken)
        val arr = json.optJSONArray("progress") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val row = arr.getJSONObject(i)
                add(
                    RemoteProgress(
                        bookId = row.getString("book_id"),
                        trackId = row.getString("track_id"),
                        positionMs = row.getLong("position_ms"),
                        updatedAt = row.getString("updated_at"),
                    ),
                )
            }
        }
    }

    data class RemoteProgress(
        val bookId: String,
        val trackId: String,
        val positionMs: Long,
        val updatedAt: String,
    )
}
