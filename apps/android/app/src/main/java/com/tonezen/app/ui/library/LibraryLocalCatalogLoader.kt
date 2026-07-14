package com.tonezen.app.ui.library

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle

internal const val LIBRARY_CATALOG_PAGE_SIZE = 100

internal suspend fun loadLocalCatalogProgressively(
    catalogRepository: CatalogRepository,
    onBooksPage: suspend (List<Book>) -> Unit = {},
): Pair<List<Book>, List<Cycle>> {
    val books = catalogRepository.loadAllBooksPaged(LIBRARY_CATALOG_PAGE_SIZE, onBooksPage)
    val cycles = catalogRepository.getAllCycles(books.associateBy { it.id })
    return books to cycles
}
