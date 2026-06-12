package com.tonezen.app.data.remote

import android.content.Context
import android.net.Uri
import com.tonezen.app.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File

class DownloadRepository(
    private val context: Context,
    private val apiClient: ApiClient,
) {
    suspend fun signedUrlForTrack(accessToken: String, trackId: String): String =
        apiClient.signDownloadUrls(accessToken, listOf(trackId))
            .firstOrNull()
            ?.url
            ?: throw IllegalStateException("No signed URL")

    suspend fun downloadTrack(
        accessToken: String,
        bookId: String,
        trackId: String,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        onProgress(0f)
        val url = resolveDownloadUrl(signedUrlForTrack(accessToken, trackId))
        val dir = File(context.filesDir, "downloads/$bookId").apply { mkdirs() }
        val target = File(dir, "$trackId.mp3")
        val request = Request.Builder().url(url).build()
        apiClient.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Download failed: ${response.code}")
            val body = response.body ?: throw IllegalStateException("Empty body")
            val total = body.contentLength().coerceAtLeast(1L)
            var lastBucket = -1
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    var downloaded = 0L
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        downloaded += read
                        val bucket = ((downloaded * 100) / total).toInt()
                        if (bucket > lastBucket) {
                            lastBucket = bucket
                            onProgress((downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        onProgress(1f)
        target
    }

    suspend fun deleteLocalTrack(bookId: String, trackId: String) = withContext(Dispatchers.IO) {
        File(context.filesDir, "downloads/$bookId/$trackId.mp3").delete()
    }

    /** Storage signed URLs may be relative (/object/sign/...) or use localhost on dev hosts. */
    private fun resolveDownloadUrl(signedUrl: String): String {
        val apiBase = BuildConfig.BASE_URL.trimEnd('/')
        val absolute = when {
            signedUrl.startsWith("http://") || signedUrl.startsWith("https://") -> signedUrl
            signedUrl.startsWith("/storage/v1/") -> "$apiBase$signedUrl"
            signedUrl.startsWith("/") -> "$apiBase/storage/v1$signedUrl"
            else -> signedUrl
        }
        return rewriteLocalhostToEmulator(absolute, apiBase)
    }

    private fun rewriteLocalhostToEmulator(url: String, apiBase: String): String {
        val parsed = Uri.parse(url)
        val host = parsed.host ?: return url
        if (host != "localhost" && host != "127.0.0.1") return url
        val apiUri = Uri.parse(apiBase)
        val apiHost = apiUri.host ?: return url
        val port = parsed.port.takeIf { it != -1 } ?: apiUri.port
        val authority = if (port != -1) "$apiHost:$port" else apiHost
        return parsed.buildUpon().encodedAuthority(authority).build().toString()
    }
}
