package com.tonezen.app.data.local

import com.tonezen.app.domain.model.Book
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CatalogBooksRepository @Inject constructor(
    private val catalogDao: CatalogDao,
) {
    suspend fun getAllBooks(limit: Int? = null): List<Book> {
        val entities = if (limit != null) {
            catalogDao.getAllBooksLimited(limit)
        } else {
            catalogDao.getAllBooks()
        }
        return entities.map { it.toDomain() }
    }

    suspend fun loadAllBooksPaged(
        pageSize: Int,
        onPage: suspend (List<Book>) -> Unit = {},
    ): List<Book> = withContext(Dispatchers.IO) {
        val accumulated = mutableListOf<Book>()
        var offset = 0
        while (true) {
            val page = catalogDao.getBooksPage(pageSize, offset).map { it.toDomain() }
            if (page.isEmpty()) break
            accumulated.addAll(page)
            onPage(accumulated.toList())
            offset += page.size
            if (page.size < pageSize) break
        }
        accumulated
    }

    suspend fun getBook(bookId: String): Book? = withContext(Dispatchers.IO) {
        catalogDao.getBook(bookId)?.toDomain()
    }

    suspend fun downloadedBookIds(books: List<Book>): Set<String> {
        val withDownloads = catalogDao.getBookIdsWithDownloads().toSet()
        return books.asSequence().map { it.id }.filter { it in withDownloads }.toSet()
    }
}
