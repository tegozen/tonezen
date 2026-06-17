package com.tonezen.app.ui.library

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle

internal suspend fun loadCatalogFromRemoteWithLocalFallback(
    catalogRepository: CatalogRepository,
    accessToken: String?,
): Pair<List<Book>, List<Cycle>> =
    try {
        val books = catalogRepository.syncFromRemote(accessToken)
        val cycles = catalogRepository.getAllCycles()
        books to cycles
    } catch (_: Exception) {
        catalogRepository.getAllBooks() to catalogRepository.getAllCycles()
    }
