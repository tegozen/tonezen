package com.tonezen.app.ui.library

import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.SessionState

data class MusicTrackPreview(
    val trackId: String,
    val trackTitle: String,
    val artist: String,
    val albumTitle: String,
    val bookId: String,
)

data class MusicPlaybackUi(
    val isActive: Boolean = false,
    val trackId: String? = null,
    val trackTitle: String? = null,
    val artist: String? = null,
    val albumTitle: String? = null,
    val bookId: String? = null,
    val isPlaying: Boolean = false,
)

data class LibraryUiState(
    val sessionState: SessionState = SessionState.UNAUTHENTICATED,
    val books: List<Book> = emptyList(),
    val downloadedBookIds: Set<String> = emptySet(),
    val favoriteBookIds: Set<String> = emptySet(),
    val filter: LibraryFilterState = LibraryFilterState(),
    val showFilterSheet: Boolean = false,
    val nowPlayingTitle: String? = null,
    val musicPreview: MusicTrackPreview? = null,
    val musicPlayback: MusicPlaybackUi = MusicPlaybackUi(),
    val musicDownloadProgress: Float? = null,
    val musicPlaybackErrorRes: Int? = null,
)
