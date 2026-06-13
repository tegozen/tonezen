package com.tonezen.app.domain.library

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle

enum class LibraryContentFilter {
    ALL,
    DOWNLOADED,
}

enum class LibrarySortOrder {
    RECENTLY_PLAYED,
    TITLE,
    AUTHOR,
}

data class LibraryFilterState(
    val query: String = "",
    val contentFilter: LibraryContentFilter = LibraryContentFilter.ALL,
    val sortOrder: LibrarySortOrder = LibrarySortOrder.RECENTLY_PLAYED,
)

fun filterAndSortBooks(
    books: List<Book>,
    downloadedBookIds: Set<String>,
    filter: LibraryFilterState,
    progressUpdatedAtByBookId: Map<String, Long> = emptyMap(),
): List<Book> {
    val normalizedQuery = filter.query.trim().lowercase()
    val filtered = books.filter { book ->
        val matchesQuery = normalizedQuery.isEmpty() ||
            book.title.lowercase().contains(normalizedQuery) ||
            book.author.orEmpty().lowercase().contains(normalizedQuery)
        if (!matchesQuery) return@filter false
        when (filter.contentFilter) {
            LibraryContentFilter.ALL -> book.contentType == ContentType.AUDIOBOOK
            LibraryContentFilter.DOWNLOADED -> downloadedBookIds.contains(book.id)
        }
    }
    return when (filter.sortOrder) {
        LibrarySortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
        LibrarySortOrder.AUTHOR -> filtered.sortedBy { it.author.orEmpty().lowercase() }
        LibrarySortOrder.RECENTLY_PLAYED -> filtered.sortedByDescending { book ->
            progressUpdatedAtByBookId[book.id] ?: 0L
        }
    }
}

fun filterCycles(
    cycles: List<Cycle>,
    downloadedBookIds: Set<String>,
    filter: LibraryFilterState,
    progressUpdatedAtByBookId: Map<String, Long> = emptyMap(),
): List<Cycle> {
    val normalizedQuery = filter.query.trim().lowercase()
    val filtered = cycles.filter { cycle ->
        val matchesQuery = normalizedQuery.isEmpty() ||
            cycle.title.lowercase().contains(normalizedQuery) ||
            cycle.books.any { book ->
                book.title.lowercase().contains(normalizedQuery) ||
                book.author.orEmpty().lowercase().contains(normalizedQuery)
            }
        if (!matchesQuery) return@filter false
        when (filter.contentFilter) {
            LibraryContentFilter.ALL -> true
            LibraryContentFilter.DOWNLOADED ->
                cycle.books.any { downloadedBookIds.contains(it.id) }
        }
    }
    return when (filter.sortOrder) {
        LibrarySortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
        LibrarySortOrder.AUTHOR -> filtered.sortedBy {
            it.books.firstOrNull()?.author.orEmpty().lowercase()
        }
        LibrarySortOrder.RECENTLY_PLAYED -> filtered.sortedByDescending { cycle ->
            cycle.books.maxOfOrNull { book -> progressUpdatedAtByBookId[book.id] ?: 0L } ?: 0L
        }
    }
}
