package com.tonezen.app.ui.library

import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.progress.BookContinueState

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

data class CyclePlaybackUi(
    val cycleId: String? = null,
    val isPlaying: Boolean = false,
    val isPreparing: Boolean = false,
    val downloadProgress: Float? = null,
)

data class CycleCardState(
    val isDownloaded: Boolean = false,
    val progressFraction: Float? = null,
    val continueState: BookContinueState? = null,
    val showDownload: Boolean = false,
    val showRemoveDownload: Boolean = false,
    val isListened: Boolean = false,
)

data class LibraryUiState(
    val isSessionLoaded: Boolean = false,
    val isBootstrapComplete: Boolean = false,
    val sessionState: SessionState = SessionState.UNAUTHENTICATED,
    val isNetworkOnline: Boolean = true,
    val isLoadingCatalog: Boolean = true,
    val cycles: List<Cycle> = emptyList(),
    val cycleCardStateById: Map<String, CycleCardState> = emptyMap(),
    val tracksByBookId: Map<String, List<Track>> = emptyMap(),
    val audiobookProgressByBookId: Map<String, AudiobookProgress?> = emptyMap(),
    val cyclePlayback: CyclePlaybackUi = CyclePlaybackUi(),
    val books: List<Book> = emptyList(),
    val downloadedBookIds: Set<String> = emptySet(),
    val filter: LibraryFilterState = LibraryFilterState(),
    val showFilterSheet: Boolean = false,
    val nowPlayingTitle: String? = null,
    val musicTrackList: List<MusicListTrack> = emptyList(),
    val musicPlayback: MusicPlaybackUi = MusicPlaybackUi(),
    val musicPlaybackErrorMessage: String? = null,
    val cyclePlaybackErrorMessage: String? = null,
    val progressUpdatedAtByBookId: Map<String, Long> = emptyMap(),
)
