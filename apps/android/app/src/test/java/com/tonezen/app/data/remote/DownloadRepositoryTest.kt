package com.tonezen.app.data.remote

import android.content.Context
import android.net.Uri
import com.tonezen.app.data.local.SafeLocalStorage
import com.tonezen.app.data.remote.downloads.DownloadsRemoteApi
import com.tonezen.app.ui.MainDispatcherRule
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import java.nio.file.Files
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.Call
import org.junit.Rule
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadRepositoryTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun freshDownloadWritesPartFileBeforePromotingFinalFile() = runTest {
        withMockedDownloadUri {
            val rootDir = Files.createTempDirectory("tonezen-download-repo").toFile()
            val context = mockk<Context>()
            every { context.filesDir } returns rootDir

            val downloadsRemoteApi = mockk<DownloadsRemoteApi>()
            coEvery {
                downloadsRemoteApi.signDownloadUrls("token", listOf("track-1"))
            } returns listOf(
                DownloadsRemoteApi.SignedUrl(
                    trackId = "track-1",
                    url = "https://tonezen.tegozen.ru/storage/v1/object/sign/content/music/track-1.mp3?token=x",
                ),
            )

            val bodyBytes = byteArrayOf(1, 2, 3, 4)
            val request = Request.Builder()
                .url("https://tonezen.tegozen.ru/storage/v1/object/sign/content/music/track-1.mp3?token=x")
                .build()
            val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(bodyBytes.toResponseBody("audio/mpeg".toMediaType()))
                .build()
            val call = mockk<Call>()
            every { call.execute() } returns response

            val httpClient = mockk<OkHttpClient>()
            every { httpClient.newCall(any()) } returns call

            val repository = DownloadRepository(context, downloadsRemoteApi, httpClient)

            val progress = mutableListOf<Float>()
            val outcome = repository.downloadTrackResumable(
                accessToken = "token",
                bookId = "book-1",
                trackId = "track-1",
                bytesAlreadyDownloaded = 0L,
                totalBytesHint = null,
                onProgress = { progress.add(it) },
                isCancelled = { false },
            )

            val finalFile = SafeLocalStorage.trackFile(rootDir, "book-1", "track-1")
                ?: error("Invalid final path")
            val partFile = SafeLocalStorage.trackPartFile(rootDir, "book-1", "track-1")
                ?: error("Invalid part path")

            assertEquals(finalFile.absolutePath, outcome.finalFile.absolutePath)
            assertTrue(finalFile.isFile)
            assertFalse(partFile.exists())
            assertArrayEquals(bodyBytes, finalFile.readBytes())
            assertEquals(bodyBytes.size.toLong(), outcome.bytesDownloaded)
            advanceUntilIdle()
            assertTrue(progress.last() == 1f)
        }
    }

    @Test
    fun rangeDownloadAppendsToExistingPartBeforePromotingFinalFile() = runTest {
        withMockedDownloadUri {
            val rootDir = Files.createTempDirectory("tonezen-download-resume").toFile()
            val context = mockk<Context>()
            every { context.filesDir } returns rootDir

            val partFile = SafeLocalStorage.trackPartFile(rootDir, "book-1", "track-1")
                ?: error("Invalid part path")
            partFile.parentFile?.mkdirs()
            partFile.writeBytes(byteArrayOf(1, 2))

            val downloadsRemoteApi = mockk<DownloadsRemoteApi>()
            coEvery {
                downloadsRemoteApi.signDownloadUrls("token", listOf("track-1"))
            } returns listOf(
                DownloadsRemoteApi.SignedUrl(
                    trackId = "track-1",
                    url = "https://tonezen.tegozen.ru/storage/v1/object/sign/content/music/track-1.mp3?token=x",
                ),
            )

            val responseBytes = byteArrayOf(3, 4)
            val request = Request.Builder()
                .url("https://tonezen.tegozen.ru/storage/v1/object/sign/content/music/track-1.mp3?token=x")
                .build()
            val response = Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(206)
                .message("Partial Content")
                .body(responseBytes.toResponseBody("audio/mpeg".toMediaType()))
                .build()
            val call = mockk<Call>()
            every { call.execute() } returns response

            val httpClient = mockk<OkHttpClient>()
            every { httpClient.newCall(any()) } returns call

            val repository = DownloadRepository(context, downloadsRemoteApi, httpClient)

            val outcome = repository.downloadTrackResumable(
                accessToken = "token",
                bookId = "book-1",
                trackId = "track-1",
                bytesAlreadyDownloaded = 2L,
                totalBytesHint = 4L,
                onProgress = {},
                isCancelled = { false },
            )

            assertArrayEquals(byteArrayOf(1, 2, 3, 4), outcome.finalFile.readBytes())
            assertEquals(4L, outcome.bytesDownloaded)
            assertFalse(partFile.exists())
        }
    }

    private suspend fun withMockedDownloadUri(block: suspend () -> Unit) {
        val parsedUri = mockk<Uri>()
        every { parsedUri.host } returns "tonezen.tegozen.ru"
        mockkStatic(Uri::class)
        try {
            every { Uri.parse(any()) } returns parsedUri
            block()
        } finally {
            unmockkStatic(Uri::class)
        }
    }
}
