package com.tonezen.app.data.remote.catalog

import com.tonezen.app.data.remote.getRemoteJson
import com.tonezen.app.data.waveformPeaksFromJsonArray
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.model.normalizeAuthor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONArray
import org.json.JSONObject

class CatalogRemoteApi(
    private val apiRoot: String,
    private val httpClient: OkHttpClient,
) {
    suspend fun fetchBooks(accessToken: String?): List<Book> = withContext(Dispatchers.IO) {
        (fetchCyclesInternal(accessToken).flatMap { it.books } + fetchMusicAlbumsInternal(accessToken))
            .distinctBy { it.id }
    }

    suspend fun fetchCycles(accessToken: String?): List<Cycle> = withContext(Dispatchers.IO) {
        fetchCyclesInternal(accessToken)
    }

    suspend fun fetchBookDetail(bookId: String, accessToken: String?): Pair<Book, List<Track>> =
        withContext(Dispatchers.IO) {
            val json = getRemoteJson(httpClient, "$apiRoot/catalog/books/$bookId", accessToken)
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
                        artist = normalizeAuthor(
                            if (t.isNull("artist")) null else t.optString("artist"),
                        ),
                        durationMs = t.optLong("duration_ms").takeIf { it > 0 },
                        localPath = null,
                        waveformPeaks = waveformPeaksFromJsonArray(t.optJSONArray("waveform_peaks")),
                    ),
                )
            }
            book to tracks
        }

    private fun fetchCyclesInternal(accessToken: String?): List<Cycle> {
        val cyclesJson = getRemoteJson(httpClient, "$apiRoot/catalog/cycles", accessToken)
        val cyclesArray = cyclesJson.optJSONArray("cycles") ?: JSONArray()
        return buildList {
            for (i in 0 until cyclesArray.length()) {
                add(parseCycle(cyclesArray.getJSONObject(i)))
            }
        }
    }

    private fun fetchMusicAlbumsInternal(accessToken: String?): List<Book> {
        val music = getRemoteJson(httpClient, "$apiRoot/catalog/music", accessToken)
        val albums = music.optJSONArray("albums") ?: JSONArray()
        return buildList {
            for (i in 0 until albums.length()) {
                add(parseBook(albums.getJSONObject(i)))
            }
        }
    }

    private fun parseBook(json: JSONObject): Book = Book(
        id = json.getString("id"),
        slug = json.getString("slug"),
        contentType = if (json.getString("content_type") == "music") ContentType.MUSIC else ContentType.AUDIOBOOK,
        title = json.getString("title"),
        author = normalizeAuthor(
            if (json.isNull("author")) null else json.optString("author"),
        ),
    )

    private fun parseCycle(json: JSONObject): Cycle {
        val booksArray = json.optJSONArray("books") ?: JSONArray()
        val books = buildList {
            for (index in 0 until booksArray.length()) {
                add(parseBook(booksArray.getJSONObject(index)))
            }
        }
        val booksBySlug = books.associateBy { it.slug }
        val bookOrder = json.optJSONArray("book_order")?.let { order ->
            buildList {
                for (index in 0 until order.length()) {
                    add(order.getString(index))
                }
            }
        } ?: books.map { it.slug }
        val orderedBooks = bookOrder.mapNotNull { booksBySlug[it] }
        return Cycle(
            id = json.getString("id"),
            slug = json.getString("slug"),
            title = json.getString("title"),
            bookOrder = bookOrder,
            books = orderedBooks.ifEmpty { books },
        )
    }
}
