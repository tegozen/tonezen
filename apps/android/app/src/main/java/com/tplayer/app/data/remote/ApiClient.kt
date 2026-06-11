package com.tplayer.app.data.remote

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.tplayer.app.domain.model.AudiobookProgress
import com.tplayer.app.domain.model.Book
import com.tplayer.app.domain.model.ContentType
import com.tplayer.app.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ApiClient(
    private val baseUrl: String,
    private val httpClient: OkHttpClient = OkHttpClient(),
) {
    suspend fun fetchBooks(accessToken: String?): List<Book> = withContext(Dispatchers.IO) {
        val cycles = getJson("$baseUrl/catalog/cycles", accessToken)
        val books = mutableListOf<Book>()
        val cyclesArray = cycles.optJSONArray("cycles") ?: JSONArray()
        for (i in 0 until cyclesArray.length()) {
            val cycle = cyclesArray.getJSONObject(i)
            val booksArray = cycle.optJSONArray("books") ?: JSONArray()
            for (j in 0 until booksArray.length()) {
                val b = booksArray.getJSONObject(j)
                books.add(parseBook(b))
            }
        }
        val music = getJson("$baseUrl/catalog/music", accessToken)
        val albums = music.optJSONArray("albums") ?: JSONArray()
        for (i in 0 until albums.length()) {
            books.add(parseBook(albums.getJSONObject(i)))
        }
        books.distinctBy { it.id }
    }

    suspend fun fetchBookDetail(bookId: String, accessToken: String?): Pair<Book, List<Track>> =
        withContext(Dispatchers.IO) {
            val json = getJson("$baseUrl/catalog/books/$bookId", accessToken)
            val book = parseBook(json)
            val tracks = mutableListOf<Track>()
            val arr = json.optJSONArray("tracks") ?: JSONArray()
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                tracks.add(
                    Track(
                        id = t.getString("id"),
                        bookId = bookId,
                        sortOrder = t.getInt("sort_order"),
                        title = t.getString("title"),
                        filename = t.getString("filename"),
                        durationMs = t.optLong("duration_ms").takeIf { it > 0 },
                        localPath = null,
                    ),
                )
            }
            book to tracks
        }

    suspend fun pushProgress(accessToken: String, bookId: String, progress: AudiobookProgress) =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("track_id", progress.trackId)
                .put("position_ms", progress.positionMs)
                .put("updated_at", java.time.Instant.ofEpochMilli(progress.updatedAtEpochMs).toString())
                .toString()
            val request = Request.Builder()
                .url("$baseUrl/progress/audiobooks/$bookId")
                .put(body.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $accessToken")
                .build()
            httpClient.newCall(request).execute().use { /* response handled by caller */ }
        }

    private fun getJson(url: String, accessToken: String?): JSONObject {
        val builder = Request.Builder().url(url)
        accessToken?.let { builder.header("Authorization", "Bearer $it") }
        httpClient.newCall(builder.build()).execute().use { response ->
            return JSONObject(response.body?.string().orEmpty())
        }
    }

    private fun parseBook(json: JSONObject): Book = Book(
        id = json.getString("id"),
        slug = json.getString("slug"),
        contentType = if (json.getString("content_type") == "music") ContentType.MUSIC else ContentType.AUDIOBOOK,
        title = json.getString("title"),
        author = json.optString("author").takeIf { it.isNotBlank() },
    )
}

fun Context.isNetworkAvailable(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
