package com.tonezen.app.ui.library

import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.SessionState

data class LibraryUiState(
    val sessionState: SessionState = SessionState.UNAUTHENTICATED,
    val books: List<Book> = emptyList(),
    val downloadedBookIds: Set<String> = emptySet(),
    val favoriteBookIds: Set<String> = emptySet(),
    val filter: LibraryFilterState = LibraryFilterState(),
    val showFilterSheet: Boolean = false,
    val nowPlayingTitle: String? = null,
    val isRefreshing: Boolean = false,
)
