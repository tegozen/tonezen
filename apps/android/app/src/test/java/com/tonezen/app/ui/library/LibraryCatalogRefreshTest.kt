package com.tonezen.app.ui.library

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.remote.RemoteHttpException
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryCatalogRefreshTest {
    private val catalogRepository = mockk<CatalogRepository>()
    private val localBook = Book("book-1", "slug", ContentType.AUDIOBOOK, "Title", null)
    private val localCycle = Cycle("cycle-1", "cycle", "Cycle", listOf("slug"), listOf(localBook))

    @Test
    fun loadCatalogFromRemoteWithLocalFallback_returnsRemoteOnSuccess() = runTest {
        coEvery { catalogRepository.syncFromRemote("token") } returns listOf(localBook)
        coEvery { catalogRepository.getAllCycles() } returns listOf(localCycle)

        val (books, cycles) = loadCatalogFromRemoteWithLocalFallback(catalogRepository, "token")

        assertEquals(listOf(localBook), books)
        assertEquals(listOf(localCycle), cycles)
        coVerify(exactly = 0) { catalogRepository.getAllBooks() }
    }

    @Test
    fun loadCatalogFromRemoteWithLocalFallback_fallsBackToLocalOnRemoteHttpException() = runTest {
        coEvery { catalogRepository.syncFromRemote(null) } throws RemoteHttpException(502, "Remote GET failed (502)")
        coEvery { catalogRepository.getAllBooks() } returns listOf(localBook)
        coEvery { catalogRepository.getAllCycles() } returns listOf(localCycle)

        val (books, cycles) = loadCatalogFromRemoteWithLocalFallback(catalogRepository, null)

        assertEquals(listOf(localBook), books)
        assertEquals(listOf(localCycle), cycles)
        coVerify(exactly = 1) { catalogRepository.getAllBooks() }
        coVerify(exactly = 1) { catalogRepository.getAllCycles() }
    }

}
