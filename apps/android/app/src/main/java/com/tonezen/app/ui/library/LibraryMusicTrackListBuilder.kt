package com.tonezen.app.ui.library

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track

internal fun toMusicListTrack(
    book: Book,
    track: Track,
    downloadedTrackIds: Set<String>,
): MusicListTrack = MusicListTrack(
    trackId = track.id,
    trackTitle = track.title,
    artist = book.author ?: book.title,
    albumTitle = book.title,
    bookId = book.id,
    durationMs = track.durationMs,
    isDownloaded = track.id in downloadedTrackIds,
)

internal fun refreshMusicTrackListDownloadState(
    list: List<MusicListTrack>,
    downloadedTrackIds: Set<String>,
): List<MusicListTrack> = list.map { item ->
    item.copy(isDownloaded = item.trackId in downloadedTrackIds)
}

internal fun buildMusicTrackListForCatalogUpdate(
    existing: List<MusicListTrack>,
    candidates: List<Pair<Book, Track>>,
    musicStartedInSession: Boolean,
    downloadedTrackIds: Set<String>,
): List<MusicListTrack> {
    if (candidates.isEmpty()) return emptyList()
    val built = buildMusicTrackListFromCandidates(
        candidates = candidates,
        shuffle = false,
        downloadedTrackIds = downloadedTrackIds,
    )
    if (existing.isEmpty()) {
        return buildMusicTrackListFromCandidates(
            candidates = candidates,
            shuffle = !musicStartedInSession,
            downloadedTrackIds = downloadedTrackIds,
        )
    }

    val existingIds = existing.map { it.trackId }.toSet()
    val builtIds = built.map { it.trackId }.toSet()
    val catalogChanged = built.size != existing.size ||
        built.any { it.trackId !in existingIds } ||
        existing.any { it.trackId !in builtIds }

    if (!catalogChanged) {
        return refreshMusicTrackListDownloadState(existing, downloadedTrackIds)
    }

    if (musicStartedInSession) {
        val freshById = built.associateBy { it.trackId }
        val kept = existing.mapNotNull { freshById[it.trackId] }
        val keptIds = kept.map { it.trackId }.toSet()
        val appended = built.filter { it.trackId !in keptIds }
        return kept + appended
    }

    return buildMusicTrackListFromCandidates(
        candidates = candidates,
        shuffle = true,
        downloadedTrackIds = downloadedTrackIds,
    )
}

internal fun buildMusicTrackListFromCandidates(
    candidates: List<Pair<Book, Track>>,
    shuffle: Boolean,
    downloadedTrackIds: Set<String>,
): List<MusicListTrack> {
    if (candidates.isEmpty()) return emptyList()
    val ordered = if (shuffle) candidates.shuffled() else candidates
    return ordered.map { (book, track) -> toMusicListTrack(book, track, downloadedTrackIds) }
}
