package com.tonezen.app.ui.shell

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.ui.components.BottomDestination

data class AppShellUiState(
    val currentTab: BottomDestination = BottomDestination.Music,
    val selectedCycle: Cycle? = null,
    val selectedBook: Book? = null,
    val autoResumeBookId: String? = null,
    val nowPlayingTitle: String? = null,
    val nowPlayingSubtitle: String? = null,
    val nowPlayingCoverSeed: String? = null,
    val isPlaying: Boolean = false,
    val showMiniPlayer: Boolean = false,
    val showExpandedPlayer: Boolean = false,
)
