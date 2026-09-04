package com.tonezen.app.domain.progress

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track

data class CycleResumeTarget(
    val book: Book,
    val track: Track,
    val startPositionMs: Long,
)

fun resolveCycleResumeTarget(
    cycle: Cycle,
    tracksByBookId: Map<String, List<Track>>,
    progressByBookId: Map<String, AudiobookProgress?>,
): CycleResumeTarget? {
    var bestBook: Book? = null
    var bestProgress: AudiobookProgress? = null
    for (bookSlug in cycle.bookOrder) {
        val book = cycle.books.find { it.slug == bookSlug } ?: continue
        val progress = progressByBookId[book.id] ?: continue
        val tracks = tracksByBookId[book.id].orEmpty()
        if (!hasMeaningfulAudiobookProgress(tracks, progress)) continue
        if (bestProgress == null || progress.updatedAtEpochMs >= bestProgress.updatedAtEpochMs) {
            bestBook = book
            bestProgress = progress
        }
    }
    if (bestBook != null && bestProgress != null) {
        return resolveBookResumeTarget(cycle, bestBook, tracksByBookId, bestProgress)
    }
    return firstCycleTarget(cycle, tracksByBookId)
}

fun resolveCycleContinueState(
    cycle: Cycle,
    tracksByBookId: Map<String, List<Track>>,
    progressByBookId: Map<String, AudiobookProgress?>,
): BookContinueState? {
    var best: Pair<BookContinueState, Long>? = null
    for (bookId in cycle.books.map { it.id }.toSet()) {
        val progress = progressByBookId[bookId] ?: continue
        if (progress.bookId != bookId) continue
        val tracks = tracksByBookId[bookId].orEmpty()
        if (resolveBookListenedMs(tracks, progress) <= 0L) continue
        val state = canContinueBookListening(bookId, tracks, progress) ?: continue
        if (best == null || progress.updatedAtEpochMs >= best.second) {
            best = state to progress.updatedAtEpochMs
        }
    }
    return best?.first
}

private fun resolveBookResumeTarget(
    cycle: Cycle,
    book: Book,
    tracksByBookId: Map<String, List<Track>>,
    progress: AudiobookProgress,
): CycleResumeTarget? {
    val tracks = tracksByBookId[book.id].orEmpty().sortedBy { it.sortOrder }
    val firstTrack = tracks.firstOrNull() ?: return null
    val trackIndex = tracks.indexOfFirst { it.id == progress.trackId }
    if (trackIndex < 0) return CycleResumeTarget(book, firstTrack, 0L)

    val track = tracks[trackIndex]
    val durationMs = track.durationMs ?: 0L
    val isComplete = durationMs > 0L &&
        progress.positionMs >= (durationMs * COMPLETED_FRACTION_THRESHOLD).toLong()
    if (!isComplete) return CycleResumeTarget(book, track, progress.positionMs)
    if (trackIndex < tracks.lastIndex) return CycleResumeTarget(book, tracks[trackIndex + 1], 0L)

    val bookIndex = cycle.bookOrder.indexOf(book.slug)
    if (bookIndex in 0 until cycle.bookOrder.lastIndex) {
        val nextBook = cycle.books.find { it.slug == cycle.bookOrder[bookIndex + 1] }
        val nextTrack = nextBook?.let { next ->
            tracksByBookId[next.id].orEmpty().sortedBy { it.sortOrder }.firstOrNull()
        }
        if (nextBook != null && nextTrack != null) return CycleResumeTarget(nextBook, nextTrack, 0L)
    }
    return firstCycleTarget(cycle, tracksByBookId)
}

private fun firstCycleTarget(
    cycle: Cycle,
    tracksByBookId: Map<String, List<Track>>,
): CycleResumeTarget? {
    val firstBook = cycle.bookOrder.firstOrNull()
        ?.let { slug -> cycle.books.find { it.slug == slug } }
        ?: cycle.books.firstOrNull()
        ?: return null
    val firstTrack = tracksByBookId[firstBook.id].orEmpty().sortedBy { it.sortOrder }.firstOrNull()
        ?: return null
    return CycleResumeTarget(firstBook, firstTrack, 0L)
}
