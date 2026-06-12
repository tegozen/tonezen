package com.tonezen.app.data.remote

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class ApiClient(
    baseUrl: String,
    val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val apiRoot = "${baseUrl.trimEnd('/')}/api/v1"
    suspend fun fetchBooks(accessToken: String?): List<Book> = withContext(Dispatchers.IO) {
        val cycles = getJson("$apiRoot/catalog/cycles", accessToken)
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
        val music = getJson("$apiRoot/catalog/music", accessToken)
        val albums = music.optJSONArray("albums") ?: JSONArray()
        for (i in 0 until albums.length()) {
            books.add(parseBook(albums.getJSONObject(i)))
        }
        books.distinctBy { it.id }
    }

    suspend fun fetchBookDetail(bookId: String, accessToken: String?): Pair<Book, List<Track>> =
        withContext(Dispatchers.IO) {
            val json = getJson("$apiRoot/catalog/books/$bookId", accessToken)
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
        val json = getJson("$apiRoot/progress/audiobooks", accessToken)
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
