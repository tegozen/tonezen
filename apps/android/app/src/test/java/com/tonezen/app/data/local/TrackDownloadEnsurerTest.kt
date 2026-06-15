package com.tonezen.app.data.local

import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.domain.model.Track
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import java.io.File
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackDownloadEnsurerTest {
    private val catalogRepository = mockk<CatalogRepository>()
    private val downloadRepository = mockk<DownloadRepository>()
    private val sessionRepository = mockk<SessionRepository>()
    private val networkMonitor = mockk<NetworkMonitor>()

    private val ensurer = TrackDownloadEnsurer(
        catalogRepository = catalogRepository,
        downloadRepository = downloadRepository,
        sessionRepository = sessionRepository,
        networkMonitor = networkMonitor,
    )

    private val track = Track("t1", "b1", 0, "Song", "song.mp3", null, 180_000, null)
    private val session = StoredSession(
        userId = "u1",
        email = "u@test.local",
        displayName = "User",
        accessToken = "token",
        refreshToken = "refresh",
        expiresAtEpochSeconds = 9_999_999_999L,
    )

    @Test
    fun returnsExistingLocalPathWithoutDownload() = runTest {
        coEvery { catalogRepository.resolveLocalTrackPath("b1", "t1") } returns "/local/t1.mp3"

        val outcome = ensurer.ensureTrackLocal("b1", track)

        assertEquals("/local/t1.mp3", outcome.track?.localPath)
        coVerify(exactly = 0) { downloadRepository.downloadTrack(any(), any(), any(), any()) }
    }

    @Test
    fun succeedsWhenMarkFailsButFileIsOnDisk() = runTest {
        coEvery { catalogRepository.resolveLocalTrackPath("b1", "t1") } returnsMany listOf(null, "/local/t1.mp3")
        coEvery { networkMonitor.isOnline() } returns true
        coEvery { sessionRepository.loadSession() } returns session
        coEvery { sessionRepository.refreshIfNeeded(session) } returns session
        coEvery {
            downloadRepository.downloadTrack(
                accessToken = "token",
                bookId = "b1",
                trackId = "t1",
                onProgress = any(),
            )
        } returns File("/local/t1.mp3")
        coEvery { catalogRepository.markTrackDownloaded("b1", "t1", "/local/t1.mp3") } returns false

        val outcome = ensurer.ensureTrackLocal("b1", track)

        assertNotNull(outcome.track)
        assertEquals("/local/t1.mp3", outcome.track?.localPath)
        assertNull(outcome.failure)
    }

    @Test
    fun failsWhenMarkFailsAndFileMissing() = runTest {
        coEvery { catalogRepository.resolveLocalTrackPath("b1", "t1") } returns null
        coEvery { networkMonitor.isOnline() } returns true
        coEvery { sessionRepository.loadSession() } returns session
        coEvery { sessionRepository.refreshIfNeeded(session) } returns session
        coEvery {
            downloadRepository.downloadTrack(
                accessToken = "token",
                bookId = "b1",
                trackId = "t1",
                onProgress = any(),
            )
        } returns File("/local/t1.mp3")
        coEvery { catalogRepository.markTrackDownloaded("b1", "t1", "/local/t1.mp3") } returns false

        val outcome = ensurer.ensureTrackLocal("b1", track)

        assertNull(outcome.track)
        assertEquals(EnsureTrackOutcome.Failure.DOWNLOAD_FAILED, outcome.failure)
    }

    @Test
    fun deduplicatesConcurrentDownloadsForSameTrack() = runTest {
        coEvery { catalogRepository.resolveLocalTrackPath("b1", "t1") } returnsMany listOf(null, "/local/t1.mp3")
        coEvery { networkMonitor.isOnline() } returns true
        coEvery { sessionRepository.loadSession() } returns session
        coEvery { sessionRepository.refreshIfNeeded(session) } returns session
        coEvery {
            downloadRepository.downloadTrack(
                accessToken = "token",
                bookId = "b1",
                trackId = "t1",
                onProgress = any(),
            )
        } coAnswers {
            kotlinx.coroutines.delay(50)
            File("/local/t1.mp3")
        }
        coEvery { catalogRepository.markTrackDownloaded("b1", "t1", "/local/t1.mp3") } returns true

        val results = listOf(
            async { ensurer.ensureTrackLocal("b1", track) },
            async { ensurer.ensureTrackLocal("b1", track) },
        ).awaitAll()

        assertTrue(results.all { it.track?.localPath == "/local/t1.mp3" })
        coVerify(exactly = 1) {
            downloadRepository.downloadTrack(
                accessToken = "token",
                bookId = "b1",
                trackId = "t1",
                onProgress = any(),
            )
        }
    }
}
