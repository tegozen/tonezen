package com.tonezen.app.domain.progress

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track

internal const val CYCLE_COMPLETED_FRACTION_THRESHOLD = 0.95f

fun resolveBookListenedMs(
    tracks: List<Track>,
    progress: AudiobookProgress?,
): Long {
    if (progress == null) return 0L
    val sorted = tracks.sortedBy { it.sortOrder }
    val progressIndex = sorted.indexOfFirst { it.id == progress.trackId }
    if (progressIndex < 0) {
        // Tracks not loaded yet — still treat a non-zero head as real listen progress.
        return progress.positionMs.coerceAtLeast(0L)
    }
    val listenedBefore = sorted.take(progressIndex).sumOf { it.durationMs ?: 0L }
    val currentDuration = sorted[progressIndex].durationMs ?: 0L
    val positionMs = if (currentDuration > 0L) {
        progress.positionMs.coerceAtMost(currentDuration)
    } else {
        progress.positionMs
    }
    return listenedBefore + positionMs
}

fun resolveBookListenFraction(
    tracks: List<Track>,
    progress: AudiobookProgress?,
): Float? {
    val sorted = tracks.sortedBy { it.sortOrder }
    if (sorted.isEmpty()) return null

    val totalMs = sorted.sumOf { it.durationMs ?: 0L }
    val fraction = if (totalMs > 0L) {
        resolveBookListenedMs(sorted, progress).toFloat() / totalMs.toFloat()
    } else {
        val progressIndex = progress?.let { saved ->
            sorted.indexOfFirst { it.id == saved.trackId }
        } ?: -1
        if (progressIndex < 0 || progress == null || progress.positionMs <= 0L) {
            0f
        } else {
            (progressIndex.toFloat() + 0.5f) / sorted.size.toFloat()
        }
    }
    return fraction.coerceIn(0f, 1f)
}

/** Real listen head (not an «unlistened» reset at first chapter @ 0). */
fun hasMeaningfulAudiobookProgress(
    tracks: List<Track>,
    progress: AudiobookProgress?,
): Boolean {
    if (progress == null) return false
    return resolveBookListenedMs(tracks, progress) > 0L
}

fun resolveCycleListenFraction(
    cycle: Cycle,
    tracksByBookId: Map<String, List<Track>>,
    progressByBookId: Map<String, AudiobookProgress?>,
): Float? {
    var totalMs = 0L
    var listenedMs = 0L
    var totalTracks = 0
    var completedTrackWeight = 0f
    for (bookSlug in cycle.bookOrder) {
        val book = cycle.books.find { it.slug == bookSlug } ?: continue
        val tracks = tracksByBookId[book.id].orEmpty().sortedBy { it.sortOrder }
        val bookTotalMs = tracks.sumOf { it.durationMs ?: 0L }
        val progress = progressByBookId[book.id]
        if (bookTotalMs > 0L) {
            totalMs += bookTotalMs
            val bookFraction = resolveBookListenFraction(tracks, progress) ?: 0f
            listenedMs += (bookFraction * bookTotalMs).toLong()
            continue
        }
        // Durations unknown: fall back to chapter index so Continue/bar still appear.
        if (tracks.isEmpty()) continue
        totalTracks += tracks.size
        completedTrackWeight += (resolveBookListenFraction(tracks, progress) ?: 0f) * tracks.size
    }
    if (totalMs > 0L) {
        return (listenedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
    }
    if (totalTracks > 0 && completedTrackWeight > 0f) {
        return (completedTrackWeight / totalTracks.toFloat()).coerceIn(0f, 1f)
    }
    return null
}

fun isCycleFullyListened(
    cycle: Cycle,
    tracksByBookId: Map<String, List<Track>>,
    progressByBookId: Map<String, AudiobookProgress?>,
): Boolean = (resolveCycleListenFraction(cycle, tracksByBookId, progressByBookId) ?: 0f) >=
    CYCLE_COMPLETED_FRACTION_THRESHOLD
