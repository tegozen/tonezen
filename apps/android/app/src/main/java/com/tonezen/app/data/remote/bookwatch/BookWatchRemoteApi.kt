package com.tonezen.app.data.remote.bookwatch

import com.tonezen.app.data.remote.getRemoteJson
import java.time.Instant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class BookWatchRemoteApi(private val apiRoot: String, private val client: OkHttpClient) {
    data class Snapshot(val watches: List<JSONObject>, val events: List<JSONObject>)
    suspend fun snapshot(token: String): Snapshot = withContext(Dispatchers.IO) {
        val json = getRemoteJson(client, "$apiRoot/book-watch", token)
        Snapshot(json.arrayObjects("watches"), json.arrayObjects("events"))
    }
    suspend fun enqueue(token: String) = withContext(Dispatchers.IO) { post("$apiRoot/book-watch/checks", token, "{}") }
    suspend fun markRead(token: String, ids: List<String>) =
        withContext(Dispatchers.IO) { post("$apiRoot/book-watch/events/read", token, JSONObject().put("event_ids", JSONArray(ids)).toString()) }
    suspend fun update(token: String, watchId: String, body: JSONObject) =
        withContext(Dispatchers.IO) { put("$apiRoot/book-watch/watches/$watchId", token, body.toString()) }
    private fun post(url: String, token: String, body: String) = request("POST", url, token, body)
    private fun put(url: String, token: String, body: String) = request("PUT", url, token, body)
    private fun request(method: String, url: String, token: String, body: String) {
        val request = Request.Builder().url(url).header("Authorization", "Bearer $token")
            .method(method, body.toRequestBody("application/json".toMediaType())).build()
        client.newCall(request).execute().use { if (!it.isSuccessful) error("Book watch HTTP ${it.code}") }
    }
    private fun JSONObject.arrayObjects(name: String): List<JSONObject> {
        val array = optJSONArray(name) ?: JSONArray()
        return List(array.length()) { array.getJSONObject(it) }
    }
    companion object { fun epoch(value: String?): Long? = value?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } }
}
