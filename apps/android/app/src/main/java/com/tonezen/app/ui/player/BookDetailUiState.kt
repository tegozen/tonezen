package com.tonezen.app.ui.player

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track

enum class SyncDisplayStatus {
    NONE,
    SYNCED,
    PENDING,
}

data class BookDetailUiState(
    val book: Book? = null,
    val tracks: List<Track> = emptyList(),
    val activeTrackId: String? = null,
    val downloadProgress: Float? = null,
    val syncStatus: SyncDisplayStatus = SyncDisplayStatus.NONE,
    val showDownloadSheet: Boolean = false,
    val showTrackActions: Boolean = false,
    val actionTrack: Track? = null,
    val estimatedDownloadBytes: Long = 0L,
    val error: String? = null,
)
