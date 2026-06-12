package com.tonezen.app.ui.shell

import com.tonezen.app.domain.model.Book
import com.tonezen.app.ui.components.BottomDestination

data class AppShellUiState(
    val currentTab: BottomDestination = BottomDestination.Library,
    val selectedBook: Book? = null,
    val nowPlayingTitle: String? = null,
    val nowPlayingSubtitle: String? = null,
    val nowPlayingCoverSeed: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val showMiniPlayer: Boolean = false,
    val showExpandedPlayer: Boolean = false,
)
