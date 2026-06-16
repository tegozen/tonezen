package com.tonezen.app.playback

import android.content.Context
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.DownloadQueueDao
import com.tonezen.app.data.local.DownloadQueueEntity
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.ResumableDownloadOutcome
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.data.local.SafeLocalStorage
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.EnqueueDownloadRequest
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.playback.TrackDownloadLocks

class TrackDownloadQueueE2ETest {
    @Before
    fun setUpMain() {
        Dispatchers.setMain(Dispatchers.Unconfined)
    }

    @After
    fun tearDownMain() {
        Dispatchers.resetMain()
    }

    private class InMemoryDownloadQueueDao : DownloadQueueDao {
        private val items = ConcurrentHashMap<Pair<String, String>, DownloadQueueEntity>()

        override suspend fun getAll(): List<DownloadQueueEntity> =
            items.values.sortedBy { it.enqueuedAt }

        override suspend fun get(bookId: String, trackId: String): DownloadQueueEntity? =
            items[bookId to trackId]

        override suspend fun upsert(item: DownloadQueueEntity) {
            items[item.bookId to item.trackId] = item
        }

        override suspend fun upsertAll(items: List<DownloadQueueEntity>) {
            items.forEach { upsert(it) }
        }

        override suspend fun delete(bookId: String, trackId: String) {
            items.remove(bookId to trackId)
        }

        override suspend fun deleteByBatch(batchId: String) {
            items.entries.removeIf { it.value.batchId == batchId }
        }

        override suspend fun deleteAll() {
            items.clear()
        }

        override suspend fun updateProgress(
            bookId: String,
            trackId: String,
            bytesDownloaded: Long,
            totalBytes: Long?,
            tempPath: String?,
        ) {
            val existing = items[bookId to trackId] ?: return
            items[bookId to trackId] = existing.copy(
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                tempPath = tempPath,
            )
        }
    }

    @Test
    fun downloadRepository_skipsHttpWhenTrackExistsUnderAnotherBookFolder() = runBlocking {
        val rootDir = Files.createTempDirectory("tonezen-q-e2e-cross-book").toFile()
        rootDir.mkdirs()

        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns rootDir

        val existing = SafeLocalStorage.trackFile(rootDir, "book-a", "t1")!!
        existing.parentFile?.mkdirs()
        existing.writeBytes(byteArrayOf(1))

        val downloadsRemoteApi = mockk<com.tonezen.app.data.remote.downloads.DownloadsRemoteApi>(relaxed = true)
        val httpClient = mockk<okhttp3.OkHttpClient>(relaxed = true)
        val downloadRepository = DownloadRepository(context, downloadsRemoteApi, httpClient)

        val outcome = downloadRepository.downloadTrackResumable(
            accessToken = "token",
            bookId = "book-b",
            trackId = "t1",
            bytesAlreadyDownloaded = 0L,
            totalBytesHint = null,
            onProgress = {},
            isCancelled = { false },
        )

        assertEquals(existing.absolutePath, outcome.finalFile.absolutePath)
        coVerify(exactly = 0) { downloadsRemoteApi.signDownloadUrls(any(), any()) }
    }

    @Test
    fun singleTrackDownload_doesNotReDownloadWhenDuplicateQueuedForSameTrackId() = runBlocking {
        val rootDir = Files.createTempDirectory("tonezen-q-e2e").toFile()
        rootDir.mkdirs()

        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns rootDir

        val dao = InMemoryDownloadQueueDao()
        val notifier = DownloadQueueNotifier()
        val localLibraryNotifier = LocalLibraryNotifier()

        val catalogRepository = mockk<CatalogRepository>(relaxed = true)
        coEvery { catalogRepository.resolveLocalTrackPath(any(), any()) } returns null
        coEvery { catalogRepository.markTrackDownloaded(any(), any(), any()) } returns true

        val session = StoredSession(
            userId = "u1",
            email = "u@test.local",
            displayName = "User",
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
            val args = it.invocation.args
            val bookId = args[1] as String
            val trackId = args[2] as String
            val onProgress = args[5] as (Float) -> Unit
            val finalFile = com.tonezen.app.data.local.SafeLocalStorage
                .trackFile(rootDir, bookId, trackId)
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

        val queueController = TrackDownloadQueueController(
            context = context,
            downloadQueueDao = dao,
            catalogRepository = catalogRepository,
            downloadRepository = downloadRepository,
            sessionRepository = sessionRepository,
            networkMonitor = networkMonitor,
            notifier = notifier,
            localLibraryNotifier = localLibraryNotifier,
            trackDownloadLocks = TrackDownloadLocks(),
        )

        val r1 = async {
            queueController.awaitTrack(
                bookId = "b1",
                trackId = "t1",
                priority = DownloadPriority.USER,
                title = "Song",
                subtitle = "Artist",
                contentType = ContentType.MUSIC.name.lowercase(),
            )
        }
        val r2 = async {
            queueController.awaitTrack(
                bookId = "b2",
                trackId = "t1",
                priority = DownloadPriority.USER,
                title = "Song",
                subtitle = "Artist",
                contentType = ContentType.MUSIC.name.lowercase(),
            )
        }

        val (out1, out2) = awaitAll(r1, r2)
        assertEquals(DownloadAwaitResult.COMPLETED, out1)
        assertEquals(DownloadAwaitResult.COMPLETED, out2)

        // The file exists on disk, so the worker should not re-download it for the second queue entry.
        coVerify(exactly = 1) {
            downloadRepository.downloadTrackResumable(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
        assertFalse(notifier.snapshot().isActive)
    }

    @Test
    fun playbackAwaitTrack_returnsCompletedWithoutDownloadingWhenFileOnDisk() = runBlocking {
        val rootDir = Files.createTempDirectory("tonezen-q-e2e-playback").toFile()
        rootDir.mkdirs()

        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns rootDir

        val dao = InMemoryDownloadQueueDao()
        val notifier = DownloadQueueNotifier()
        val localLibraryNotifier = LocalLibraryNotifier()

        val catalogRepository = mockk<CatalogRepository>(relaxed = true)
        coEvery { catalogRepository.resolveLocalTrackPath(any(), any()) } returns null
        coEvery { catalogRepository.markTrackDownloaded(any(), any(), any()) } returns true

        val session = StoredSession(
            userId = "u1",
            email = "u@test.local",
            displayName = "User",
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

        // Put the file where TrackDownloadQueueController's on-disk resolver expects it.
        val file = com.tonezen.app.data.local.SafeLocalStorage
            .trackFile(rootDir, "b1", "t1")
            ?: error("Invalid track path")
        file.parentFile?.mkdirs()
        file.writeBytes(byteArrayOf(1))

        val queueController = TrackDownloadQueueController(
            context = context,
            downloadQueueDao = dao,
            catalogRepository = catalogRepository,
            downloadRepository = downloadRepository,
            sessionRepository = sessionRepository,
            networkMonitor = networkMonitor,
            notifier = notifier,
            localLibraryNotifier = localLibraryNotifier,
            trackDownloadLocks = TrackDownloadLocks(),
        )

        val result = queueController.awaitTrack(
            bookId = "b1",
            trackId = "t1",
            priority = DownloadPriority.PLAY,
            title = "Song",
            subtitle = "Artist",
            contentType = ContentType.MUSIC.name.lowercase(),
        )

        assertEquals(DownloadAwaitResult.COMPLETED, result)
        coVerify(exactly = 0) { downloadRepository.downloadTrackResumable(any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun bulkDownload_completesAndStopsWorker_whenAllFilesOnDiskAfterDownloads() = runBlocking {
        val rootDir = Files.createTempDirectory("tonezen-q-e2e-bulk").toFile()
        rootDir.mkdirs()

        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns rootDir

        val dao = InMemoryDownloadQueueDao()
        val notifier = DownloadQueueNotifier()
        val localLibraryNotifier = LocalLibraryNotifier()

        val catalogRepository = mockk<CatalogRepository>(relaxed = true)
        coEvery { catalogRepository.resolveLocalTrackPath(any(), any()) } returns null
        coEvery { catalogRepository.markTrackDownloaded(any(), any(), any()) } returns true

        val session = StoredSession(
            userId = "u1",
            email = "u@test.local",
            displayName = "User",
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
            val args = it.invocation.args
            val bookId = args[1] as String
            val trackId = args[2] as String
            val onProgress = args[5] as (Float) -> Unit
            val finalFile = com.tonezen.app.data.local.SafeLocalStorage
                .trackFile(rootDir, bookId, trackId)
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

        val queueController = TrackDownloadQueueController(
            context = context,
            downloadQueueDao = dao,
            catalogRepository = catalogRepository,
            downloadRepository = downloadRepository,
            sessionRepository = sessionRepository,
            networkMonitor = networkMonitor,
            notifier = notifier,
            localLibraryNotifier = localLibraryNotifier,
            trackDownloadLocks = TrackDownloadLocks(),
        )

        val batchId = "batch-1"
        val now = System.currentTimeMillis()
        val req1 = EnqueueDownloadRequest(
            bookId = "b1",
            trackId = "t1",
            priority = DownloadPriority.BULK,
            batchId = batchId,
            title = "Song1",
            subtitle = null,
            contentType = ContentType.MUSIC.name.lowercase(),
            enqueuedAt = now - 10,
        )
        val req2 = EnqueueDownloadRequest(
            bookId = "b1",
            trackId = "t2",
            priority = DownloadPriority.BULK,
            batchId = batchId,
            title = "Song2",
            subtitle = null,
            contentType = ContentType.MUSIC.name.lowercase(),
            enqueuedAt = now,
        )

        queueController.enqueueBatch(listOf(req1, req2), batchId = batchId)

        // enqueueBatch runs asynchronously; wait until items appear, then until they're processed.
        withTimeout(10_000) {
            while (dao.getAll().isEmpty()) {
                delay(20)
            }
        }
        withTimeout(10_000) {
            while (dao.getAll().isNotEmpty()) {
                delay(50)
            }
        }

        coVerify(exactly = 2) {
            downloadRepository.downloadTrackResumable(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
            )
        }
        assertFalse(notifier.snapshot().isActive)
    }

    @Test
    fun awaitTrack_waitsThroughTransientFailuresUntilSuccess() = runBlocking {
        val rootDir = Files.createTempDirectory("tonezen-q-e2e-retry").toFile()
        rootDir.mkdirs()

        val context = mockk<Context>(relaxed = true)
        every { context.filesDir } returns rootDir

        val dao = InMemoryDownloadQueueDao()
        val notifier = DownloadQueueNotifier()
        val localLibraryNotifier = LocalLibraryNotifier()

        val catalogRepository = mockk<CatalogRepository>(relaxed = true)
        coEvery { catalogRepository.resolveLocalTrackPath(any(), any()) } returns null
        coEvery { catalogRepository.markTrackDownloaded(any(), any(), any()) } returns true

        val session = StoredSession(
            userId = "u1",
            email = "u@test.local",
            displayName = "User",
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

        val attempts = AtomicInteger(0)
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
            if (attempts.incrementAndGet() < 2) throw IOException("transient")
            val bookId = it.invocation.args[1] as String
            val trackId = it.invocation.args[2] as String
            val finalFile = SafeLocalStorage.trackFile(rootDir, bookId, trackId)
                ?: error("Invalid track path")
            finalFile.parentFile?.mkdirs()
            finalFile.writeBytes(byteArrayOf(1))
            ResumableDownloadOutcome(
                finalFile = finalFile,
                bytesDownloaded = finalFile.length(),
                totalBytes = 1L,
            )
        }

        val queueController = TrackDownloadQueueController(
            context = context,
            downloadQueueDao = dao,
            catalogRepository = catalogRepository,
            downloadRepository = downloadRepository,
            sessionRepository = sessionRepository,
            networkMonitor = networkMonitor,
            notifier = notifier,
            localLibraryNotifier = localLibraryNotifier,
            trackDownloadLocks = TrackDownloadLocks(),
        )

        val result = queueController.awaitTrack(
            bookId = "b1",
            trackId = "t1",
            priority = DownloadPriority.PLAY,
            title = "Song",
            subtitle = "Artist",
            contentType = ContentType.MUSIC.name.lowercase(),
        )

        assertEquals(DownloadAwaitResult.COMPLETED, result)
        assertEquals(2, attempts.get())
        assertTrue(dao.getAll().isEmpty())
    }
}

