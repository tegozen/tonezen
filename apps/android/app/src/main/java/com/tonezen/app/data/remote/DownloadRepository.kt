package com.tonezen.app.data.remote

import android.content.Context
import com.tonezen.app.data.local.SafeLocalStorage
import com.tonezen.app.data.remote.downloads.DownloadsRemoteApi
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient

@Singleton
class DownloadRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadsRemoteApi: DownloadsRemoteApi,
    private val httpClient: OkHttpClient,
) {
    private val activeCall = AtomicReference<Call?>(null)
    private val transfer = TrackDownloadTransfer(
        context = context,
        httpClient = httpClient,
        activeCall = activeCall,
        signedUrlForTrack = ::signedUrlForTrack,
    )

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
    ): ResumableDownloadOutcome = transfer.downloadTrackResumable(
        accessToken = accessToken,
        bookId = bookId,
        trackId = trackId,
        bytesAlreadyDownloaded = bytesAlreadyDownloaded,
        totalBytesHint = totalBytesHint,
        onProgress = onProgress,
        isCancelled = isCancelled,
    )

    suspend fun deleteLocalTrack(bookId: String, trackId: String) = withContext(Dispatchers.IO) {
        SafeLocalStorage.trackFile(context.filesDir, bookId, trackId)?.delete()
        SafeLocalStorage.trackPartFile(context.filesDir, bookId, trackId)?.delete()
    }
}
