package com.tonezen.app.domain.progress

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Track

private const val COMPLETED_FRACTION_THRESHOLD = 0.95f

data class BookContinueState(
    val trackTitle: String,
    val positionMs: Long,
)

fun canContinueBookListening(
    bookId: String,
    tracks: List<Track>,
    progress: AudiobookProgress?,
): BookContinueState? {
    if (bookId.isBlank() || progress == null || progress.bookId != bookId || tracks.isEmpty()) return null

    val sortedTracks = tracks.sortedBy { it.sortOrder }
    val savedTrack = sortedTracks.find { it.id == progress.trackId && it.bookId == bookId } ?: return null

    val isBookListened = sortedTracks.all { track ->
        track.sortOrder < savedTrack.sortOrder ||
            (track.id == savedTrack.id &&
                progress.positionMs >= (track.durationMs ?: 0L) * COMPLETED_FRACTION_THRESHOLD)
    }
    if (isBookListened) return null

    val progressByTrack = buildBookTrackProgress(
        tracks = sortedTracks,
        savedTrackId = progress.trackId,
        savedPositionMs = progress.positionMs,
        activeTrackId = null,
        livePositionMs = 0L,
    )
    val fraction = progressByTrack[savedTrack.id] ?: return null
    if (fraction <= 0f || fraction >= COMPLETED_FRACTION_THRESHOLD) return null

    return BookContinueState(trackTitle = savedTrack.title, positionMs = progress.positionMs)
}

fun buildBookTrackProgress(
    tracks: List<Track>,
    savedTrackId: String?,
    savedPositionMs: Long,
    activeTrackId: String?,
    livePositionMs: Long,
): Map<String, Float> {
    val sortedTracks = tracks.sortedBy { it.sortOrder }
    val progressTrack = sortedTracks.find { it.id == savedTrackId }
    val map = mutableMapOf<String, Float>()

    for (track in sortedTracks) {
        when {
            track.id == activeTrackId && track.durationMs != null && track.durationMs > 0L -> {
                map[track.id] = (livePositionMs.toFloat() / track.durationMs.toFloat()).coerceIn(0f, 1f)
            }
            track.id == savedTrackId && track.durationMs != null && track.durationMs > 0L -> {
                map[track.id] = (savedPositionMs.toFloat() / track.durationMs.toFloat()).coerceIn(0f, 1f)
            }
            progressTrack != null && track.sortOrder < progressTrack.sortOrder -> {
                map[track.id] = 1f
            }
        }
    }

    return map
}
