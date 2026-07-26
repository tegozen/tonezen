package com.tonezen.app.domain.progress

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Track

private const val COMPLETED_FRACTION_THRESHOLD = 0.95f

data class BookContinueState(
    val trackTitle: String,
    val positionMs: Long,
)

/** Where Continue / resume should actually start (advances past a ≥95% chapter). */
data class BookContinuePlayHead(
    val track: Track,
    val positionMs: Long,
)

fun resolveBookContinuePlayHead(
    tracks: List<Track>,
    progress: AudiobookProgress?,
): BookContinuePlayHead? {
    val sortedTracks = tracks.sortedBy { it.sortOrder }
    if (sortedTracks.isEmpty()) return null
    if (progress == null) {
        return BookContinuePlayHead(sortedTracks.first(), 0L)
    }
    val index = sortedTracks.indexOfFirst { it.id == progress.trackId }
    if (index < 0) {
        return BookContinuePlayHead(sortedTracks.first(), 0L)
    }
    val track = sortedTracks[index]
    val durationMs = track.durationMs ?: 0L
    val isComplete = durationMs > 0L &&
        progress.positionMs >= (durationMs * COMPLETED_FRACTION_THRESHOLD).toLong()
    if (!isComplete) {
        return BookContinuePlayHead(track, progress.positionMs.coerceAtLeast(0L))
    }
    if (index < sortedTracks.lastIndex) {
        return BookContinuePlayHead(sortedTracks[index + 1], 0L)
    }
    return null
}

fun canContinueBookListening(
    bookId: String,
    tracks: List<Track>,
    progress: AudiobookProgress?,
): BookContinueState? {
    if (bookId.isBlank() || progress == null || progress.bookId != bookId || tracks.isEmpty()) return null

    val sortedTracks = tracks.sortedBy { it.sortOrder }
    if (!hasMeaningfulAudiobookProgress(sortedTracks, progress)) return null
    if (isBookFullyListened(sortedTracks, progress)) return null

    val head = resolveBookContinuePlayHead(sortedTracks, progress) ?: return null
    return BookContinueState(trackTitle = head.track.title, positionMs = head.positionMs)
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
