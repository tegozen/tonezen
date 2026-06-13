package com.tonezen.app.domain.library

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
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
            filter = LibraryFilterState(contentFilter = LibraryContentFilter.DOWNLOADED),
        )
        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun filtersAllShowsAudiobooksOnly() {
        val result = filterAndSortBooks(
            books = books,
            downloadedBookIds = emptySet(),
            filter = LibraryFilterState(contentFilter = LibraryContentFilter.ALL),
        )
        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun filtersByQueryWithinAudiobooks() {
        val result = filterAndSortBooks(
            books = books,
            downloadedBookIds = emptySet(),
            filter = LibraryFilterState(query = "alpha"),
        )
        assertEquals(listOf("1"), result.map { it.id })
    }

    @Test
    fun sortsCyclesByRecentlyPlayed() {
        val cycles = listOf(
            Cycle(
                id = "c1",
                slug = "alpha",
                title = "Alpha",
                bookOrder = listOf("a"),
                books = listOf(Book("1", "a", ContentType.AUDIOBOOK, "Alpha", null)),
            ),
            Cycle(
                id = "c2",
                slug = "beta",
                title = "Beta",
                bookOrder = listOf("b"),
                books = listOf(Book("2", "b", ContentType.AUDIOBOOK, "Beta", null)),
            ),
        )
        val result = filterCycles(
            cycles = cycles,
            downloadedBookIds = emptySet(),
            filter = LibraryFilterState(sortOrder = LibrarySortOrder.RECENTLY_PLAYED),
            progressUpdatedAtByBookId = mapOf("1" to 100L, "2" to 200L),
        )
        assertEquals(listOf("c2", "c1"), result.map { it.id })
    }
}
