package com.tonezen.app.ui.player

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track

data class BookDetailUiState(
    val book: Book? = null,
    val tracks: List<Track> = emptyList(),
    val progressTrackTitle: String? = null,
    val downloadProgress: Float? = null,
    val nowPlayingTitle: String? = null,
    val isPlaying: Boolean = false,
    val error: String? = null,
)
