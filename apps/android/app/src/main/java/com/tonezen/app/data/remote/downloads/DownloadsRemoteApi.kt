package com.tonezen.app.data.remote.downloads

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

internal class DownloadsRemoteApi(
    private val apiRoot: String,
    private val httpClient: OkHttpClient,
) {
    suspend fun signDownloadUrls(accessToken: String, trackIds: List<String>): List<SignedUrl> =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("track_ids", JSONArray(trackIds)).toString()
            val request = Request.Builder()
                .url("$apiRoot/downloads/sign")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $accessToken")
                .build()
            httpClient.newCall(request).execute().use { response ->
                val json = JSONObject(response.body?.string().orEmpty())
                val arr = json.optJSONArray("urls") ?: JSONArray()
                buildList {
                    for (i in 0 until arr.length()) {
                        val item = arr.getJSONObject(i)
                        add(SignedUrl(item.getString("track_id"), item.getString("url")))
                    }
                }
            }
        }

    data class SignedUrl(val trackId: String, val url: String)
}
