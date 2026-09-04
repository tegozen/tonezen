package com.tonezen.app.domain.progress

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track

fun orderedCycleBooks(cycle: Cycle): List<Book> {
    val ordered = cycle.bookOrder.mapNotNull { slug -> cycle.books.find { it.slug == slug } }
    return ordered.ifEmpty { cycle.books }
}

fun findCycleContainingBook(cycles: List<Cycle>, bookId: String): Cycle? =
    cycles.firstOrNull { cycle -> cycle.books.any { it.id == bookId } }

fun resolveEarlierCycleBookConfirm(
    cycle: Cycle,
    startingBook: Book,
    tracksByBookId: Map<String, List<Track>>,
    progressByBookId: Map<String, AudiobookProgress?>,
): Book? {
    val ordered = orderedCycleBooks(cycle)
    val startIndex = ordered.indexOfFirst { it.id == startingBook.id }
    if (startIndex < 0 || startIndex >= ordered.lastIndex) return null

    val startingProgress = progressByBookId[startingBook.id]
    val startingTracks = tracksByBookId[startingBook.id].orEmpty()
    val startingUpdatedAt = if (hasMeaningfulAudiobookProgress(startingTracks, startingProgress)) {
        requireNotNull(startingProgress).updatedAtEpochMs
    } else {
        Long.MIN_VALUE
    }

    var bestLater: Book? = null
    var bestUpdatedAt = Long.MIN_VALUE
    for (index in (startIndex + 1) until ordered.size) {
        val book = ordered[index]
        val progress = progressByBookId[book.id] ?: continue
        if (!hasMeaningfulAudiobookProgress(tracksByBookId[book.id].orEmpty(), progress)) continue
        if (progress.updatedAtEpochMs < startingUpdatedAt) continue
        if (progress.updatedAtEpochMs >= bestUpdatedAt) {
            bestUpdatedAt = progress.updatedAtEpochMs
            bestLater = book
        }
    }
    return bestLater
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
        for (track in tracksByBookId[book.id].orEmpty().sortedBy { it.sortOrder }) {
            if (!reachedResume && book.id == resume.book.id && track.id == resume.track.id) {
                reachedResume = true
            }
            if (reachedResume) entries += book to track
        }
    }
    return entries
}
