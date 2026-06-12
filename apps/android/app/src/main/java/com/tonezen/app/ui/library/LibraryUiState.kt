package com.tonezen.app.ui.library

import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.SessionState

data class MusicListTrack(
    val trackId: String,
    val trackTitle: String,
    val artist: String,
    val albumTitle: String,
    val bookId: String,
    val durationMs: Long? = null,
    val isDownloaded: Boolean,
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
    val isLoadingCatalog: Boolean = true,
    val cycles: List<Cycle> = emptyList(),
    val books: List<Book> = emptyList(),
    val downloadedBookIds: Set<String> = emptySet(),
    val filter: LibraryFilterState = LibraryFilterState(),
    val showFilterSheet: Boolean = false,
    val nowPlayingTitle: String? = null,
    val musicTrackList: List<MusicListTrack> = emptyList(),
    val musicPlayback: MusicPlaybackUi = MusicPlaybackUi(),
    val musicPlaybackErrorRes: Int? = null,
)
