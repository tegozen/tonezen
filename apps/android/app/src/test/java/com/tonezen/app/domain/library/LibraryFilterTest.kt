package com.tonezen.app.domain.library

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import org.junit.Assert.assertEquals
import org.junit.Test

class LibraryFilterTest {
    private val books = listOf(
        Book("1", "a", ContentType.AUDIOBOOK, "Alpha", "Author A"),
        Book("2", "b", ContentType.MUSIC, "Beta", "Author B"),
    )

    @Test
    fun filtersDownloadedBooks() {
        val result = filterAndSortBooks(
            books = books,
            downloadedBookIds = setOf("1"),
            favoriteBookIds = emptySet(),
            filter = LibraryFilterState(contentFilter = LibraryContentFilter.DOWNLOADED),
        )
        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun filtersByQuery() {
        val result = filterAndSortBooks(
            books = books,
            downloadedBookIds = emptySet(),
            favoriteBookIds = emptySet(),
            filter = LibraryFilterState(query = "beta"),
        )
        assertEquals(listOf("2"), result.map { it.id })
    }
}
