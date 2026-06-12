package com.tonezen.app.domain.library

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle

enum class LibraryContentFilter {
    ALL,
    AUDIOBOOKS,
    MUSIC,
    DOWNLOADED,
    FAVORITES,
}

enum class LibrarySortOrder {
    RECENTLY_PLAYED,
    TITLE,
    AUTHOR,
}

data class LibraryFilterState(
    val query: String = "",
    val contentFilter: LibraryContentFilter = LibraryContentFilter.ALL,
    val sortOrder: LibrarySortOrder = LibrarySortOrder.TITLE,
)

fun filterAndSortBooks(
    books: List<Book>,
    downloadedBookIds: Set<String>,
    favoriteBookIds: Set<String>,
    filter: LibraryFilterState,
): List<Book> {
    val normalizedQuery = filter.query.trim().lowercase()
    val filtered = books.filter { book ->
        val matchesQuery = normalizedQuery.isEmpty() ||
            book.title.lowercase().contains(normalizedQuery) ||
            book.author.orEmpty().lowercase().contains(normalizedQuery)
        if (!matchesQuery) return@filter false
        when (filter.contentFilter) {
            LibraryContentFilter.ALL -> true
            LibraryContentFilter.AUDIOBOOKS -> book.contentType == ContentType.AUDIOBOOK
            LibraryContentFilter.MUSIC -> book.contentType == ContentType.MUSIC
            LibraryContentFilter.DOWNLOADED -> downloadedBookIds.contains(book.id)
            LibraryContentFilter.FAVORITES -> favoriteBookIds.contains(book.id)
        }
    }
    return when (filter.sortOrder) {
        LibrarySortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
        LibrarySortOrder.AUTHOR -> filtered.sortedBy { it.author.orEmpty().lowercase() }
        LibrarySortOrder.RECENTLY_PLAYED -> filtered
    }
}

fun filterCycles(
    cycles: List<Cycle>,
    downloadedBookIds: Set<String>,
    favoriteBookIds: Set<String>,
    filter: LibraryFilterState,
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
            LibraryContentFilter.ALL, LibraryContentFilter.AUDIOBOOKS -> true
            LibraryContentFilter.MUSIC -> false
            LibraryContentFilter.DOWNLOADED ->
                cycle.books.any { downloadedBookIds.contains(it.id) }
            LibraryContentFilter.FAVORITES ->
                cycle.books.any { favoriteBookIds.contains(it.id) }
        }
    }
    return when (filter.sortOrder) {
        LibrarySortOrder.TITLE -> filtered.sortedBy { it.title.lowercase() }
        LibrarySortOrder.AUTHOR -> filtered.sortedBy {
            it.books.firstOrNull()?.author.orEmpty().lowercase()
        }
        LibrarySortOrder.RECENTLY_PLAYED -> filtered
    }
}
