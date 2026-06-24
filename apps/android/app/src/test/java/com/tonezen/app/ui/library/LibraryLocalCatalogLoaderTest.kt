package com.tonezen.app.ui.library

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryLocalCatalogLoaderTest {
    private val catalogRepository = mockk<CatalogRepository>()
    private val bookA = Book("book-a", "slug-a", ContentType.AUDIOBOOK, "A", null)
    private val bookB = Book("book-b", "slug-b", ContentType.AUDIOBOOK, "B", null)

    @Test
    fun loadLocalCatalogProgressively_returnsFullCatalogAndResolvesCyclesFromLoadedBooks() = runTest {
        val emittedPages = mutableListOf<List<Book>>()
        coEvery {
            catalogRepository.loadAllBooksPaged(LIBRARY_CATALOG_PAGE_SIZE, any())
        } coAnswers {
            val onPage = secondArg<suspend (List<Book>) -> Unit>()
            onPage(listOf(bookA))
            onPage(listOf(bookA, bookB))
            listOf(bookA, bookB)
        }
        coEvery { catalogRepository.getAllCycles(any()) } returns emptyList<Cycle>()

        val (books, cycles) = loadLocalCatalogProgressively(catalogRepository) { page ->
            emittedPages.add(page)
        }

        assertEquals(listOf(bookA, bookB), books)
        assertEquals(emptyList<Cycle>(), cycles)
        assertEquals(listOf(listOf(bookA), listOf(bookA, bookB)), emittedPages)
        coVerify(exactly = 1) { catalogRepository.getAllCycles(any()) }
    }
}
