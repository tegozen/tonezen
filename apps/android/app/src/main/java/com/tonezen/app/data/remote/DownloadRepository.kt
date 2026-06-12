package com.tonezen.app.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

class DownloadRepository(
    private val context: Context,
    private val apiClient: ApiClient,
) {
    suspend fun downloadTrack(
        accessToken: String,
        bookId: String,
        trackId: String,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        val signed = apiClient.signDownloadUrls(accessToken, listOf(trackId))
        val url = signed.firstOrNull()?.url ?: throw IllegalStateException("No signed URL")
        val dir = File(context.filesDir, "downloads/$bookId").apply { mkdirs() }
        val target = File(dir, "$trackId.mp3")
        val request = Request.Builder().url(url).build()
        apiClient.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Download failed: ${response.code}")
            val body = response.body ?: throw IllegalStateException("Empty body")
            val total = body.contentLength().coerceAtLeast(1L)
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded.toFloat() / total.toFloat())
                    }
                }
            }
        }
        target
    }

    suspend fun deleteLocalTrack(bookId: String, trackId: String) = withContext(Dispatchers.IO) {
        File(context.filesDir, "downloads/$bookId/$trackId.mp3").delete()
    }
}
