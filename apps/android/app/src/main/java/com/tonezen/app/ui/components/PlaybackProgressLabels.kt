package com.tonezen.app.ui.components

import com.tonezen.app.domain.model.Track

internal fun formatPlaybackProgressLabel(tracks: List<Track>, trackId: String, positionMs: Long): String {
    val title = tracks.find { it.id == trackId }?.title ?: "Глава"
    val totalSeconds = (positionMs / 1_000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$title · $minutes:${seconds.toString().padStart(2, '0')}"
}
