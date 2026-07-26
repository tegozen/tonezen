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

fun isBookFullyListened(
    tracks: List<Track>,
    progress: AudiobookProgress?,
): Boolean {
    if (tracks.isEmpty() || progress == null) return false
    val sorted = tracks.sortedBy { it.sortOrder }
    val lastTrack = sorted.lastOrNull() ?: return false
    return resolveTrackListenState(sorted, progress, lastTrack.id).status == TrackListenStatus.COMPLETED
}

fun resolveAudiobookPlaybackStartMs(
    progress: AudiobookProgress?,
    track: Track,
): Long {
    if (progress == null || progress.trackId != track.id) return 0L
    val durationMs = track.durationMs ?: return progress.positionMs
    if (durationMs <= 0L) return progress.positionMs
    if (progress.positionMs >= (durationMs * COMPLETED_FRACTION_THRESHOLD).toLong()) return 0L
    return progress.positionMs
}

sealed class AudiobookPlaybackIntent {
    data class Resume(val positionMs: Long) : AudiobookPlaybackIntent()

    data object StartFromZero : AudiobookPlaybackIntent()

    data class ConfirmEarlierChapter(
        val savedTrackId: String,
        val savedPositionMs: Long,
    ) : AudiobookPlaybackIntent()

    data class ConfirmProgressSyncConflict(
        val localTrackId: String,
        val localPositionMs: Long,
        val server: ProgressServerSnapshot,
    ) : AudiobookPlaybackIntent()
}

fun resolveAudiobookPlaybackIntent(
    sortedTracks: List<Track>,
    bookProgress: AudiobookProgress?,
    clickedTrack: Track,
    skipSyncConflictPrompt: Boolean = false,
): AudiobookPlaybackIntent {
    if (!skipSyncConflictPrompt && ProgressMerger.shouldPrompt(bookProgress)) {
        val snapshot = ProgressMerger.getServerSnapshot(bookProgress)!!
        return AudiobookPlaybackIntent.ConfirmProgressSyncConflict(
            localTrackId = bookProgress!!.trackId,
            localPositionMs = bookProgress.positionMs,
            server = snapshot,
        )
    }
    if (bookProgress == null) return AudiobookPlaybackIntent.StartFromZero
    val savedIndex = sortedTracks.indexOfFirst { it.id == bookProgress.trackId }
    val clickedIndex = sortedTracks.indexOfFirst { it.id == clickedTrack.id }
    if (savedIndex < 0 || clickedIndex < 0) return AudiobookPlaybackIntent.StartFromZero
    return when {
        clickedIndex == savedIndex -> AudiobookPlaybackIntent.Resume(
            resolveAudiobookPlaybackStartMs(bookProgress, clickedTrack),
        )
        clickedIndex > savedIndex -> AudiobookPlaybackIntent.StartFromZero
        else -> AudiobookPlaybackIntent.ConfirmEarlierChapter(
            savedTrackId = bookProgress.trackId,
            savedPositionMs = bookProgress.positionMs,
        )
    }
}
