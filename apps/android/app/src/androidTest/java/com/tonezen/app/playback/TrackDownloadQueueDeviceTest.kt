package com.tonezen.app.playback

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tonezen.app.DeviceTestVisibility
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.DownloadQueueDao
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.SafeLocalStorage
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.ResumableDownloadOutcome
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.EnqueueDownloadRequest
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.StoredSession
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.Description
import org.junit.runner.RunWith

/**
 * On-device e2e for [TrackDownloadQueueController]: real Android [Context], Room queue DAO,
 * fake network/download layer (no production auth or catalog).
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class TrackDownloadQueueDeviceTest {
    @get:org.junit.Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @Inject @ApplicationContext lateinit var context: Context
    @Inject lateinit var downloadQueueDao: DownloadQueueDao
    @Inject lateinit var downloadQueueNotifier: DownloadQueueNotifier
    @Inject lateinit var localLibraryNotifier: LocalLibraryNotifier

    private lateinit var downloadQueueController: TrackDownloadQueueController
    private lateinit var catalogRepository: CatalogRepository
    private val downloadCallCount = AtomicInteger(0)

    private val bookId1 = "device-book-1"
    private val bookId2 = "device-book-2"
    private val trackId1 = "device-track-1"
    private val trackId2 = "device-track-2"

    @get:org.junit.Rule(order = 1)
    val visibilityRule = object : org.junit.rules.TestWatcher() {
        override fun starting(description: Description) {
            DeviceTestVisibility.launchApp(description.methodName)
        }
    }

    @Before
    fun setUp() {
        hiltRule.inject()
        if (!::downloadQueueController.isInitialized) {
            downloadQueueController = createQueueController()
        }
        downloadCallCount.set(0)
        runBlocking {
            downloadQueueController.cancelAllAwait()
            downloadQueueDao.deleteAll()
        }
    }

    @After
    fun tearDown() = runBlocking {
        if (::downloadQueueController.isInitialized) {
            downloadQueueController.cancelAllAwait()
        }
    }

    @Test
    fun singleTrackDownload_completesOnce_andDoesNotRestart() = runBlocking {
        deleteLocalTrack(bookId1, trackId1)

        downloadQueueController.enqueue(enqueueRequest(bookId1, trackId1, DownloadPriority.USER))
        waitUntilQueueIdle()

        val file = requireTrackFile(bookId1, trackId1)
        assertTrue("Track file should exist after download", file.isFile && file.length() > 0L)
        assertEquals(1, downloadCallCount.get())
        assertTrue("Queue should be idle", !downloadQueueNotifier.snapshot().isActive)
        assertTrue("Queue table should be empty", downloadQueueDao.getAll().isEmpty())

        val mtimeAfterFirst = file.lastModified()

        downloadQueueController.enqueue(enqueueRequest(bookId1, trackId1, DownloadPriority.USER))
        waitUntilQueueIdle(timeoutMs = 15_000)

        assertEquals(mtimeAfterFirst, file.lastModified())
        assertEquals(1, downloadCallCount.get())
        assertTrue(downloadQueueDao.getAll().isEmpty())
    }

    @Test
    fun playbackAwaitTrack_completesWithoutRedownloadWhenFileOnDisk() = runBlocking {
        deleteLocalTrack(bookId1, trackId1)

        val first = downloadQueueController.awaitTrack(
            bookId = bookId1,
            trackId = trackId1,
            priority = DownloadPriority.PLAY,
            title = "Song",
            subtitle = "Album",
            contentType = ContentType.MUSIC.name.lowercase(),
        )
        assertEquals(DownloadAwaitResult.COMPLETED, first)

        val file = requireTrackFile(bookId1, trackId1)
        val mtimeAfterDownload = file.lastModified()
        assertEquals(1, downloadCallCount.get())

        val second = downloadQueueController.awaitTrack(
            bookId = bookId1,
            trackId = trackId1,
            priority = DownloadPriority.PLAY,
            title = "Song",
            subtitle = "Album",
            contentType = ContentType.MUSIC.name.lowercase(),
        )
        assertEquals(DownloadAwaitResult.COMPLETED, second)
        assertEquals(mtimeAfterDownload, file.lastModified())
        assertEquals(1, downloadCallCount.get())
        assertTrue(downloadQueueDao.getAll().isEmpty())
    }

    @Test
    fun bulkDownload_completesAllTracks_andStopsWorker() = runBlocking {
        deleteLocalTrack(bookId1, trackId1)
        deleteLocalTrack(bookId1, trackId2)

        val batchId = "device-e2e-batch"
        downloadQueueController.enqueueBatch(
            requests = listOf(
                enqueueRequest(bookId1, trackId1, DownloadPriority.BULK, batchId),
                enqueueRequest(bookId1, trackId2, DownloadPriority.BULK, batchId),
            ),
            batchId = batchId,
        )

        waitUntilQueueIdle(timeoutMs = 60_000)

        listOf(trackId1, trackId2).forEach { trackId ->
            val file = requireTrackFile(bookId1, trackId)
            assertTrue(file.isFile && file.length() > 0L)
        }
        assertEquals(2, downloadCallCount.get())
        assertTrue(downloadQueueDao.getAll().isEmpty())
        assertTrue(!downloadQueueNotifier.snapshot().isActive)
    }

    @Test
    fun duplicateTrackIdAcrossBookIds_downloadsOnce() = runBlocking {
        deleteLocalTrack(bookId1, trackId1)
        deleteLocalTrack(bookId2, trackId1)

        val (r1, r2) = coroutineScope {
            val a1 = async { awaitTrackFor(bookId1, trackId1) }
            val a2 = async { awaitTrackFor(bookId2, trackId1) }
            a1.await() to a2.await()
        }

        assertEquals(DownloadAwaitResult.COMPLETED, r1)
        assertEquals(DownloadAwaitResult.COMPLETED, r2)
        assertEquals(1, downloadCallCount.get())

        val onDisk = SafeLocalStorage.trackFile(context.filesDir, bookId1, trackId1)
            ?: SafeLocalStorage.trackFile(context.filesDir, bookId2, trackId1)
        assertTrue(onDisk != null && onDisk.isFile && onDisk.length() > 0L)
        assertTrue(downloadQueueDao.getAll().isEmpty())
    }

    private fun createQueueController(): TrackDownloadQueueController {
        catalogRepository = mockk(relaxed = true)
        coEvery { catalogRepository.resolveLocalTrackPath(any(), any()) } returns null
        coEvery { catalogRepository.markTrackDownloaded(any(), any(), any()) } returns true
        coEvery { catalogRepository.clearTrackLocalPath(any(), any()) } returns Unit

        val session = StoredSession(
            userId = "device-test",
            email = "device@test.local",
            displayName = "Device",
            accessToken = "token",
            refreshToken = "refresh",
            expiresAtEpochSeconds = 9_999_999_999L,
        )
        val sessionRepository = mockk<SessionRepository>(relaxed = true)
        every { sessionRepository.loadSession() } returns session
        coEvery { sessionRepository.refreshIfNeeded(any()) } returns session

        val networkMonitor = mockk<NetworkMonitor>(relaxed = true)
        every { networkMonitor.isOnline() } returns true
        every { networkMonitor.online } returns MutableStateFlow(true)

        val downloadRepository = mockk<DownloadRepository>(relaxed = true)
        coEvery {
            downloadRepository.downloadTrackResumable(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        } coAnswers {
            downloadCallCount.incrementAndGet()
            val args = it.invocation.args
            val bookId = args[1] as String
            val trackId = args[2] as String
            val onProgress = args[5] as (Float) -> Unit
            val finalFile = SafeLocalStorage.trackFile(context.filesDir, bookId, trackId)
                ?: error("Invalid track path")
            finalFile.parentFile?.mkdirs()
            finalFile.writeBytes(byteArrayOf(1))
            onProgress(1f)
            ResumableDownloadOutcome(
                finalFile = finalFile,
                bytesDownloaded = finalFile.length(),
                totalBytes = 1L,
            )
        }

        return TrackDownloadQueueController(
            context = context,
            downloadQueueDao = downloadQueueDao,
            catalogRepository = catalogRepository,
            downloadRepository = downloadRepository,
            sessionRepository = sessionRepository,
            networkMonitor = networkMonitor,
            notifier = downloadQueueNotifier,
            localLibraryNotifier = localLibraryNotifier,
        )
    }

    private suspend fun awaitTrackFor(bookId: String, trackId: String): DownloadAwaitResult =
        downloadQueueController.awaitTrack(
            bookId = bookId,
            trackId = trackId,
            priority = DownloadPriority.USER,
            title = "Song",
            subtitle = "Album",
            contentType = ContentType.MUSIC.name.lowercase(),
        )

    private fun enqueueRequest(
        bookId: String,
        trackId: String,
        priority: DownloadPriority,
        batchId: String? = null,
    ) = EnqueueDownloadRequest(
        bookId = bookId,
        trackId = trackId,
        priority = priority,
        batchId = batchId,
        title = "Song",
        subtitle = "Album",
        contentType = ContentType.MUSIC.name.lowercase(),
    )

    private suspend fun waitUntilQueueIdle(timeoutMs: Long = 60_000) {
        withTimeout(timeoutMs) {
            while (true) {
                val snapshot = downloadQueueNotifier.snapshot()
                val queueEmpty = downloadQueueDao.getAll().isEmpty()
                if (queueEmpty && snapshot.activeTrackId == null) break
                delay(100)
            }
            delay(200)
        }
    }

    private fun requireTrackFile(bookId: String, trackId: String): File =
        SafeLocalStorage.trackFile(context.filesDir, bookId, trackId)
            ?: error("Invalid track path")

    private suspend fun deleteLocalTrack(bookId: String, trackId: String) {
        downloadQueueDao.delete(bookId, trackId)
        SafeLocalStorage.trackFile(context.filesDir, bookId, trackId)?.delete()
        SafeLocalStorage.trackPartFile(context.filesDir, bookId, trackId)?.delete()
        catalogRepository.clearTrackLocalPath(bookId, trackId)
    }
}
