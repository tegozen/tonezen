package com.tonezen.app.data.remote

import com.tonezen.app.data.remote.catalog.CatalogRemoteApi
import com.tonezen.app.data.remote.downloads.DownloadsRemoteApi
import com.tonezen.app.data.remote.progress.ProgressRemoteApi
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track
import okhttp3.OkHttpClient

class ApiClient(
    baseUrl: String,
    val httpClient: OkHttpClient = OkHttpClient(),
) {
    private val apiRoot = "${baseUrl.trimEnd('/')}/api/v1"
    private val catalog = CatalogRemoteApi(apiRoot, httpClient)
    private val downloads = DownloadsRemoteApi(apiRoot, httpClient)
    private val progress = ProgressRemoteApi(apiRoot, httpClient)

    suspend fun fetchBooks(accessToken: String?): List<Book> = catalog.fetchBooks(accessToken)

    suspend fun fetchCycles(accessToken: String?): List<Cycle> = catalog.fetchCycles(accessToken)

    suspend fun fetchBookDetail(bookId: String, accessToken: String?): Pair<Book, List<Track>> =
        catalog.fetchBookDetail(bookId, accessToken)

    suspend fun signDownloadUrls(accessToken: String, trackIds: List<String>): List<SignedUrl> =
        downloads.signDownloadUrls(accessToken, trackIds).map { SignedUrl(it.trackId, it.url) }

    suspend fun pushProgress(accessToken: String, bookId: String, progress: AudiobookProgress) =
        this.progress.pushProgress(accessToken, bookId, progress)

    suspend fun fetchProgress(accessToken: String): List<RemoteProgress> =
        progress.fetchProgress(accessToken).map {
            RemoteProgress(it.bookId, it.trackId, it.positionMs, it.updatedAt)
        }

    data class SignedUrl(val trackId: String, val url: String)

    data class RemoteProgress(
        val bookId: String,
        val trackId: String,
        val positionMs: Long,
        val updatedAt: String,
    )
}
