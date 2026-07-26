package com.tonezen.app.data.local

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CatalogCyclesRepository @Inject constructor(
    private val catalogDao: CatalogDao,
    private val booksRepository: CatalogBooksRepository,
) {
    suspend fun getAllCycles(booksById: Map<String, Book>? = null): List<Cycle> = withContext(Dispatchers.IO) {
        val resolvedBooksById = booksById ?: booksRepository.getAllBooks().associateBy { it.id }
        catalogDao.getAllCycles().mapNotNull { it.toDomain(resolvedBooksById) }
    }
}
