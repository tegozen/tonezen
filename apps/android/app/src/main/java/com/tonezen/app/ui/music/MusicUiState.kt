package com.tonezen.app.ui.music

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

data class MusicUiState(
    val isNetworkOnline: Boolean = true,
    val isLoadingCatalog: Boolean = true,
    val hasMusicBooks: Boolean = false,
    val musicTrackList: List<MusicListTrack> = emptyList(),
    val musicPlayback: MusicPlaybackUi = MusicPlaybackUi(),
    val musicPlaybackErrorMessage: String? = null,
    val nowPlayingTitle: String? = null,
)
