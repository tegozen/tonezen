package com.tonezen.app.domain.progress

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track

private const val COMPLETED_FRACTION_THRESHOLD = 0.95f

data class CycleResumeTarget(
    val book: Book,
    val track: Track,
    val startPositionMs: Long,
)

fun resolveBookListenedMs(
    tracks: List<Track>,
    progress: AudiobookProgress?,
): Long {
    if (progress == null) return 0L
    val sorted = tracks.sortedBy { it.sortOrder }
    val progressIndex = sorted.indexOfFirst { it.id == progress.trackId }
    if (progressIndex < 0) return 0L
    val listenedBefore = sorted.take(progressIndex).sumOf { it.durationMs ?: 0L }
    val currentDuration = sorted[progressIndex].durationMs ?: 0L
    val positionMs = if (currentDuration > 0L) {
        progress.positionMs.coerceAtMost(currentDuration)
    } else {
        progress.positionMs
    }
    return listenedBefore + positionMs
}

fun resolveCycleListenFraction(
    cycle: Cycle,
    tracksByBookId: Map<String, List<Track>>,
    progressByBookId: Map<String, AudiobookProgress?>,
): Float? {
    var totalMs = 0L
    var listenedMs = 0L
    for (bookSlug in cycle.bookOrder) {
        val book = cycle.books.find { it.slug == bookSlug } ?: continue
        val tracks = tracksByBookId[book.id].orEmpty()
        val bookTotalMs = tracks.sumOf { it.durationMs ?: 0L }
        if (bookTotalMs <= 0L) continue
        totalMs += bookTotalMs
        listenedMs += resolveBookListenedMs(tracks, progressByBookId[book.id])
    }
    if (totalMs <= 0L) return null
    return (listenedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
}

fun isCycleFullyListened(
    cycle: Cycle,
    tracksByBookId: Map<String, List<Track>>,
    progressByBookId: Map<String, AudiobookProgress?>,
): Boolean = (resolveCycleListenFraction(cycle, tracksByBookId, progressByBookId) ?: 0f) >=
    COMPLETED_FRACTION_THRESHOLD

fun resolveCycleResumeTarget(
    cycle: Cycle,
    tracksByBookId: Map<String, List<Track>>,
    progressByBookId: Map<String, AudiobookProgress?>,
): CycleResumeTarget? {
    // Most recently listened book wins (same clock as Continue UI). Ignore reset / pos-0 heads.
    var bestBook: Book? = null
    var bestProgress: AudiobookProgress? = null
    for (bookSlug in cycle.bookOrder) {
        val book = cycle.books.find { it.slug == bookSlug } ?: continue
        val progress = progressByBookId[book.id] ?: continue
        val tracks = tracksByBookId[book.id].orEmpty()
        if (resolveBookListenedMs(tracks, progress) <= 0L) continue
        if (bestProgress == null || progress.updatedAtEpochMs >= bestProgress.updatedAtEpochMs) {
            bestBook = book
            bestProgress = progress
        }
    }
    if (bestBook != null && bestProgress != null) {
        return resolveBookResumeTarget(cycle, bestBook, tracksByBookId, bestProgress)
    }
    val firstBook = cycle.bookOrder.firstOrNull()
        ?.let { slug -> cycle.books.find { it.slug == slug } }
        ?: cycle.books.firstOrNull()
        ?: return null
    val firstTracks = tracksByBookId[firstBook.id].orEmpty().sortedBy { it.sortOrder }
    val firstTrack = firstTracks.firstOrNull() ?: return null
    return CycleResumeTarget(firstBook, firstTrack, 0L)
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
    if (trackIndex < 0) {
        return CycleResumeTarget(book, firstTrack, 0L)
    }
    val track = tracks[trackIndex]
    val durationMs = track.durationMs ?: 0L
    val isTrackComplete = durationMs > 0L &&
        progress.positionMs >= (durationMs * COMPLETED_FRACTION_THRESHOLD).toLong()
    if (!isTrackComplete) {
        return CycleResumeTarget(book, track, progress.positionMs)
    }
    if (trackIndex < tracks.lastIndex) {
        return CycleResumeTarget(book, tracks[trackIndex + 1], 0L)
    }
    val bookIndex = cycle.bookOrder.indexOf(book.slug)
    if (bookIndex in 0 until cycle.bookOrder.lastIndex) {
        val nextBook = cycle.books.find { it.slug == cycle.bookOrder[bookIndex + 1] }
        if (nextBook != null) {
            val nextTracks = tracksByBookId[nextBook.id].orEmpty().sortedBy { it.sortOrder }
            val nextTrack = nextTracks.firstOrNull()
            if (nextTrack != null) {
                return CycleResumeTarget(nextBook, nextTrack, 0L)
            }
        }
    }
    val firstBook = cycle.bookOrder.firstOrNull()
        ?.let { slug -> cycle.books.find { it.slug == slug } }
        ?: cycle.books.firstOrNull()
        ?: return null
    val firstTracks = tracksByBookId[firstBook.id].orEmpty().sortedBy { it.sortOrder }
    val restartTrack = firstTracks.firstOrNull() ?: return null
    return CycleResumeTarget(firstBook, restartTrack, 0L)
}

fun orderedCycleBooks(cycle: Cycle): List<Book> {
    val ordered = cycle.bookOrder.mapNotNull { slug -> cycle.books.find { it.slug == slug } }
    return ordered.ifEmpty { cycle.books }
}

fun findCycleContainingBook(cycles: List<Cycle>, bookId: String): Cycle? =
    cycles.firstOrNull { cycle -> cycle.books.any { it.id == bookId } }

/**
 * When starting [startingBook], if the cycle's most recent real listen is on a **later** book,
 * return that later book so UI can confirm. Same/earlier last listen → null (no prompt).
 * Play-cycle resume must not use this — it already targets the latest listen.
 */
fun resolveEarlierCycleBookConfirm(
    cycle: Cycle,
    startingBook: Book,
    tracksByBookId: Map<String, List<Track>>,
    progressByBookId: Map<String, AudiobookProgress?>,
): Book? {
    val ordered = orderedCycleBooks(cycle)
    val startIndex = ordered.indexOfFirst { it.id == startingBook.id }
    if (startIndex < 0) return null

    var bestBook: Book? = null
    var bestUpdatedAt = Long.MIN_VALUE
    for (book in ordered) {
        val progress = progressByBookId[book.id] ?: continue
        val tracks = tracksByBookId[book.id].orEmpty()
        if (resolveBookListenedMs(tracks, progress) <= 0L) continue
        if (progress.updatedAtEpochMs >= bestUpdatedAt) {
            bestUpdatedAt = progress.updatedAtEpochMs
            bestBook = book
        }
    }
    val latest = bestBook ?: return null
    val latestIndex = ordered.indexOfFirst { it.id == latest.id }
    if (latestIndex <= startIndex) return null
    return latest
}

fun resolveCycleContinueState(
    cycle: Cycle,
    tracksByBookId: Map<String, List<Track>>,
    progressByBookId: Map<String, AudiobookProgress?>,
): BookContinueState? {
    val bookIds = cycle.books.map { it.id }.toSet()
    if (bookIds.isEmpty()) return null

    var best: Pair<BookContinueState, Long>? = null

    for (bookId in bookIds) {
        val progress = progressByBookId[bookId] ?: continue
        if (progress.bookId != bookId) continue

        val tracks = tracksByBookId[bookId].orEmpty()
        if (resolveBookListenedMs(tracks, progress) <= 0L) continue

        val state = canContinueBookListening(bookId, tracks, progress) ?: continue
        val updatedAt = progress.updatedAtEpochMs
        if (best == null || updatedAt >= best.second) {
            best = state to updatedAt
        }
    }

    return best?.first
}

fun orderedCycleEntriesFromResume(
    cycle: Cycle,
    tracksByBookId: Map<String, List<Track>>,
    resume: CycleResumeTarget,
): List<Pair<Book, Track>> {
    val entries = mutableListOf<Pair<Book, Track>>()
    var reachedResume = false
    for (bookSlug in cycle.bookOrder) {
        val book = cycle.books.find { it.slug == bookSlug } ?: continue
        val tracks = tracksByBookId[book.id].orEmpty().sortedBy { it.sortOrder }
        for (track in tracks) {
            if (!reachedResume) {
                if (book.id == resume.book.id && track.id == resume.track.id) {
                    reachedResume = true
                    entries += book to track
                }
            } else {
                entries += book to track
            }
        }
    }
    return entries
}
