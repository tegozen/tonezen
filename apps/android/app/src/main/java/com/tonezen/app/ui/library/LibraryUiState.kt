package com.tonezen.app.ui.library

import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.progress.BookContinueState

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

/** A3b prompt before play-cycle when resume book's local/cloud heads diverge. */
data class CycleProgressSyncConflictPrompt(
    val cycleId: String,
    val bookId: String,
    val localLabel: String,
    val serverLabel: String,
)

data class LibraryUiState(
    val isSessionLoaded: Boolean = false,
    val isBootstrapComplete: Boolean = false,
    val hasShownInitialLocalCatalog: Boolean = false,
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
    val cyclePlaybackErrorMessage: String? = null,
    val progressUpdatedAtByBookId: Map<String, Long> = emptyMap(),
    val confirmProgressSyncConflict: CycleProgressSyncConflictPrompt? = null,
)
