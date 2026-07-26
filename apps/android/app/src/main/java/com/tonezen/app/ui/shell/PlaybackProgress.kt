package com.tonezen.app.ui.shell

/** Playback transport progress for MiniPlayer / Now Playing — isolated from chrome navigation state. */
data class PlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)
