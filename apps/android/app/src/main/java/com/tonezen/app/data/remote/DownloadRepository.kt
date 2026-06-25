package com.tonezen.app.data.remote

import android.content.Context
import android.net.Uri
import com.tonezen.app.BuildConfig
import com.tonezen.app.data.local.SafeLocalStorage
import com.tonezen.app.data.remote.downloads.DownloadsRemoteApi
import com.tonezen.app.domain.downloads.DownloadResumePolicy
import com.tonezen.app.domain.downloads.DownloadUrlPolicy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

data class ResumableDownloadOutcome(
    val finalFile: File,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
)

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadsRemoteApi: DownloadsRemoteApi,
    private val httpClient: OkHttpClient,
) {
    private val activeCall = AtomicReference<Call?>(null)

    fun cancelActiveDownload() {
        activeCall.getAndSet(null)?.cancel()
    }

    suspend fun signedUrlForTrack(accessToken: String, trackId: String): String =
        downloadsRemoteApi.signDownloadUrls(accessToken, listOf(trackId))
            .firstOrNull()
            ?.url
            ?: throw IllegalStateException("No signed URL")

    suspend fun downloadTrack(
        accessToken: String,
        bookId: String,
        trackId: String,
        onProgress: (Float) -> Unit = {},
    ): File = downloadTrackResumable(
        accessToken = accessToken,
        bookId = bookId,
        trackId = trackId,
        bytesAlreadyDownloaded = 0L,
        totalBytesHint = null,
        onProgress = onProgress,
        isCancelled = { false },
    ).finalFile

    suspend fun downloadTrackResumable(
        accessToken: String,
        bookId: String,
        trackId: String,
        bytesAlreadyDownloaded: Long,
        totalBytesHint: Long?,
        onProgress: (Float) -> Unit,
        isCancelled: () -> Boolean,
    ): ResumableDownloadOutcome = withContext(Dispatchers.IO) {
        val partFile = SafeLocalStorage.trackPartFile(context.filesDir, bookId, trackId)
            ?: throw IllegalArgumentException("Invalid download target")
        val finalFile = SafeLocalStorage.trackFile(context.filesDir, bookId, trackId)
            ?: throw IllegalArgumentException("Invalid download target")
        partFile.parentFile?.mkdirs()

        SafeLocalStorage.findDownloadedTrack(context.filesDir, trackId, bookId)?.let { existing ->
            reportDownloadProgress(onProgress, 1f, awaitDelivery = true)
            return@withContext ResumableDownloadOutcome(
                finalFile = existing.file,
                bytesDownloaded = existing.file.length(),
                totalBytes = totalBytesHint ?: existing.file.length(),
            )
        }

        var offset = bytesAlreadyDownloaded.coerceAtLeast(0L)
        if (offset == 0L) {
            partFile.delete()
        } else if (!partFile.exists()) {
            offset = 0L
        } else {
            offset = partFile.length()
        }

        val url = resolveDownloadUrl(signedUrlForTrack(accessToken, trackId))
        var totalBytes = totalBytesHint
        var attemptOffset = offset

        repeat(2) { attempt ->
            if (isCancelled()) throw IOException("Download cancelled")
            val requestBuilder = Request.Builder().url(url)
            if (attemptOffset > 0L) {
                requestBuilder.header("Range", "bytes=$attemptOffset-")
            }
            val call = httpClient.newCall(requestBuilder.build())
            activeCall.set(call)
            try {
                call.execute().use { response ->
                    val action = DownloadResumePolicy.resolveResumeAction(
                        partFileLength = partFile.length(),
                        bytesDownloaded = attemptOffset,
                        totalBytes = totalBytes,
                        rangeResponseCode = if (attemptOffset > 0L) response.code else null,
                    )
                    if (action == DownloadResumePolicy.ResumeAction.RESTART) {
                        partFile.delete()
                        attemptOffset = 0L
                        return@repeat
                    }
                    if (!response.isSuccessful && response.code != 206) {
                        throw IOException("Download failed: ${response.code}")
                    }
                    val body = response.body ?: throw IOException("Empty body")
                    val contentLength = body.contentLength()
                    val headerLength = response.header("Content-Length")?.toLongOrNull()
                    totalBytes = when {
                        response.code == 206 -> {
                            val rangeTotal = response.header("Content-Range")
                                ?.substringAfterLast('/')
                                ?.toLongOrNull()
                            rangeTotal?.takeIf { it > 0L }
                                ?: (attemptOffset + contentLength.coerceAtLeast(0L)).takeIf { contentLength > 0 }
                        }
                        contentLength > 0 -> contentLength
                        headerLength != null && headerLength > 0 -> headerLength
                        totalBytes != null -> totalBytes
                        else -> null
                    }
                    var lastBucket = -1
                    body.byteStream().use { input ->
                        val append = attemptOffset > 0L && partFile.exists() && partFile.length() == attemptOffset
                        if (!append) partFile.delete()
                        FileOutputStream(partFile, append).use { output ->
                            val buffer = ByteArray(8192)
                            var downloaded = if (append) attemptOffset else 0L
                            var read: Int
                            while (input.read(buffer).also { read = it } != -1) {
                                if (isCancelled()) throw IOException("Download cancelled")
                                output.write(buffer, 0, read)
                                downloaded += read
                                val total = totalBytes
                                if (total != null && total > 0) {
                                    val bucket = ((downloaded * 50) / total).toInt()
                                    if (bucket > lastBucket) {
                                        lastBucket = bucket
                                        reportDownloadProgress(
                                            onProgress,
                                            DownloadResumePolicy.progressFraction(downloaded, total) ?: 0f,
                                        )
                                    }
                                } else if (downloaded > 0L) {
                                    val bucket = (downloaded / (512 * 1024)).toInt()
                                    if (bucket > lastBucket) {
                                        lastBucket = bucket
                                        reportDownloadProgress(
                                            onProgress,
                                            (0.05f + bucket * 0.05f).coerceAtMost(0.95f),
                                        )
                                    }
                                }
                            }
                            attemptOffset = downloaded
                        }
                    }
                    if (partFile.length() <= 0L) throw IOException("Download empty")
                    if (finalFile.exists()) finalFile.delete()
                    if (!partFile.renameTo(finalFile)) {
                        partFile.copyTo(finalFile, overwrite = true)
                        partFile.delete()
                    }
                    reportDownloadProgress(onProgress, 1f, awaitDelivery = true)
                    return@withContext ResumableDownloadOutcome(
                        finalFile = finalFile,
                        bytesDownloaded = finalFile.length(),
                        totalBytes = totalBytes,
                    )
                }
            } finally {
                activeCall.compareAndSet(call, null)
            }
        }
        throw IOException("Download failed after retry")
    }

    private suspend fun reportDownloadProgress(
        onProgress: (Float) -> Unit,
        fraction: Float,
        awaitDelivery: Boolean = false,
    ) {
        val deliver = { runCatching { onProgress(fraction) } }
        if (awaitDelivery) {
            withContext(Dispatchers.Main.immediate) { deliver() }
        } else {
            CoroutineScope(currentCoroutineContext()).launch(Dispatchers.Main.immediate) { deliver() }
        }
    }

    suspend fun deleteLocalTrack(bookId: String, trackId: String) = withContext(Dispatchers.IO) {
        SafeLocalStorage.trackFile(context.filesDir, bookId, trackId)?.delete()
        SafeLocalStorage.trackPartFile(context.filesDir, bookId, trackId)?.delete()
    }

    private fun resolveDownloadUrl(signedUrl: String): String {
        val apiBase = BuildConfig.BASE_URL.trimEnd('/')
        val absolute = when {
            signedUrl.startsWith("http://") || signedUrl.startsWith("https://") -> signedUrl
            signedUrl.startsWith("/storage/v1/") -> "$apiBase$signedUrl"
            signedUrl.startsWith("/") -> "$apiBase/storage/v1$signedUrl"
            else -> signedUrl
        }
        return rewriteLocalhostToEmulator(absolute, apiBase).let { resolved ->
            val normalized = DownloadUrlPolicy.normalizeDownloadUrl(resolved, apiBase)
            DownloadUrlPolicy.assertAllowedDownloadUrl(normalized, apiBase)
            normalized
        }
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
