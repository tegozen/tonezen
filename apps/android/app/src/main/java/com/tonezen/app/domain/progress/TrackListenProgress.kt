package com.tonezen.app.domain.progress

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Track

enum class TrackListenStatus {
    NOT_STARTED,
    IN_PROGRESS,
    COMPLETED,
}

data class TrackListenState(
    val status: TrackListenStatus,
    val fraction: Float = 0f,
) {
    val barFraction: Float? =
        when (status) {
            TrackListenStatus.NOT_STARTED -> null
            TrackListenStatus.IN_PROGRESS -> fraction.coerceIn(0f, 1f)
            TrackListenStatus.COMPLETED -> 1f
        }
}

private const val COMPLETED_FRACTION_THRESHOLD = 0.95f

fun resolveTrackListenState(
    sortedTracks: List<Track>,
    bookProgress: AudiobookProgress?,
    trackId: String,
    livePositionMs: Long? = null,
): TrackListenState {
    if (sortedTracks.isEmpty() || bookProgress == null) {
        return TrackListenState(TrackListenStatus.NOT_STARTED)
    }
    val progressIndex = sortedTracks.indexOfFirst { it.id == bookProgress.trackId }
    val trackIndex = sortedTracks.indexOfFirst { it.id == trackId }
    if (progressIndex < 0 || trackIndex < 0) {
        return TrackListenState(TrackListenStatus.NOT_STARTED)
    }
    when {
        trackIndex < progressIndex -> return TrackListenState(TrackListenStatus.COMPLETED, 1f)
        trackIndex > progressIndex -> return TrackListenState(TrackListenStatus.NOT_STARTED)
    }
    val track = sortedTracks[trackIndex]
    val durationMs = track.durationMs ?: 0L
    if (durationMs <= 0L) return TrackListenState(TrackListenStatus.NOT_STARTED)
    val positionMs = if (livePositionMs != null && livePositionMs > bookProgress.positionMs) {
        livePositionMs
    } else {
        bookProgress.positionMs
    }
    val fraction = (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    return when {
        fraction >= COMPLETED_FRACTION_THRESHOLD -> TrackListenState(TrackListenStatus.COMPLETED, 1f)
        positionMs > 0L -> TrackListenState(TrackListenStatus.IN_PROGRESS, fraction)
        else -> TrackListenState(TrackListenStatus.NOT_STARTED)
    }
}
