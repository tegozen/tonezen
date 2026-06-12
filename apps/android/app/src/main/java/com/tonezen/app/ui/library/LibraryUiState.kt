package com.tonezen.app.ui.library

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.SessionState

data class LibraryUiState(
    val sessionState: SessionState = SessionState.UNAUTHENTICATED,
    val books: List<Book> = emptyList(),
    val downloadedBookIds: Set<String> = emptySet(),
    val selectedBook: Book? = null,
    val nowPlayingTitle: String? = null,
)
