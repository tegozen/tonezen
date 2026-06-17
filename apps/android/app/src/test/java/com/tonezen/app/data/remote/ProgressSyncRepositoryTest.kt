package com.tonezen.app.data.remote

import com.tonezen.app.data.local.AudiobookProgressEntity
import com.tonezen.app.data.local.ProgressRepository
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.progress.ProgressRemoteApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ProgressSyncRepositoryTest {
    private val progressRemoteApi = mockk<ProgressRemoteApi>()
    private val progressRepository = mockk<ProgressRepository>()
    private val sessionRepository = mockk<SessionRepository>()
    private val networkMonitor = mockk<NetworkMonitor>()

    private val repository = ProgressSyncRepository(
        progressRemoteApi = progressRemoteApi,
        progressRepository = progressRepository,
        sessionRepository = sessionRepository,
        networkMonitor = networkMonitor,
    )

    @Test
    fun pushProgressStoresServerWinner() = runTest {
        val local = AudiobookProgressEntity(
            bookId = "book-1",
            trackId = "track-old",
            positionMs = 10_000L,
            updatedAtEpochMs = 1_700_000_000_000L,
            pendingSync = true,
        )
        coEvery {
            progressRemoteApi.pushProgress(
                accessToken = "token",
                bookId = "book-1",
                progress = any(),
            )
        } returns ProgressRemoteApi.RemoteProgress(
            bookId = "book-1",
            trackId = "track-newer",
            positionMs = 42_000L,
            updatedAt = "2024-06-01T00:00:00Z",
        )
        coEvery { progressRepository.getProgressEntity("book-1") } returns local
        coEvery { progressRepository.upsertProgressEntity(any()) } returns Unit

        repository.pushProgress("token", local)

        coVerify {
            progressRepository.upsertProgressEntity(
                match {
                    it.bookId == "book-1" &&
                        it.trackId == "track-newer" &&
                        it.positionMs == 42_000L &&
                        !it.pendingSync
                },
            )
        }
    }
}
