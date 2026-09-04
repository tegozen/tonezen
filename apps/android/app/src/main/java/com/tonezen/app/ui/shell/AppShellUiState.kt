package com.tonezen.app.ui.shell

data class AppShellUiState(
    val nowPlayingTitle: String? = null,
    val nowPlayingSubtitle: String? = null,
    val nowPlayingCoverSeed: String? = null,
    val isPlaying: Boolean = false,
    val showMiniPlayer: Boolean = false,
    val showExpandedPlayer: Boolean = false,
)
