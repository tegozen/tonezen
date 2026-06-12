package com.tonezen.app.ui.player

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track

enum class BookDetailTab {
    PLAYER,
    DETAILS,
}

enum class SyncDisplayStatus {
    NONE,
    SYNCED,
    PENDING,
}

data class BookDetailUiState(
    val book: Book? = null,
    val tracks: List<Track> = emptyList(),
    val progressTrackTitle: String? = null,
    val downloadProgress: Float? = null,
    val nowPlayingTitle: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val playbackSpeed: Float = 1f,
    val selectedTab: BookDetailTab = BookDetailTab.PLAYER,
    val syncStatus: SyncDisplayStatus = SyncDisplayStatus.NONE,
    val isFavorite: Boolean = false,
    val showDownloadSheet: Boolean = false,
    val showTrackActions: Boolean = false,
    val actionTrack: Track? = null,
    val estimatedDownloadBytes: Long = 0L,
    val error: String? = null,
)
