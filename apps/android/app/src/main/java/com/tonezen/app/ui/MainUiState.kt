package com.tonezen.app.ui

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.domain.model.Track

data class MainUiState(
    val sessionState: SessionState = SessionState.UNAUTHENTICATED,
    val books: List<Book> = emptyList(),
    val downloadedBookIds: Set<String> = emptySet(),
    val selectedBook: Book? = null,
    val tracks: List<Track> = emptyList(),
    val progressLabel: String? = null,
    val downloadProgress: Float? = null,
    val nowPlayingTitle: String? = null,
    val isPlaying: Boolean = false,
    val error: String? = null,
)
