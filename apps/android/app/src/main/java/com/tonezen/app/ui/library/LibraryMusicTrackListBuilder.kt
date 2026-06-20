package com.tonezen.app.ui.library

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicLibraryVisibilityRules

internal fun toMusicListTrack(
    book: Book,
    track: Track,
    downloadedTrackIds: Set<String>,
): MusicListTrack = MusicListTrack(
    trackId = track.id,
    trackTitle = track.title,
    artist = track.artist ?: book.author ?: book.title,
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
    shuffleNewTracks: (List<MusicListTrack>) -> List<MusicListTrack> = { it.shuffled() },
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
    val freshById = built.associateBy { it.trackId }
    val catalogChanged = built.size != existing.size ||
        built.any { it.trackId !in existingIds } ||
        existing.any { it.trackId !in builtIds } ||
        existing.any { item ->
            val fresh = freshById[item.trackId] ?: return@any false
            item.trackTitle != fresh.trackTitle ||
                item.artist != fresh.artist ||
                item.albumTitle != fresh.albumTitle ||
                item.bookId != fresh.bookId ||
                item.durationMs != fresh.durationMs
        }

    if (!catalogChanged) {
        return refreshMusicTrackListDownloadState(existing, downloadedTrackIds)
    }

    val kept = existing.mapNotNull { freshById[it.trackId] }
    val keptIds = kept.map { it.trackId }.toSet()
    val appended = built.filter { it.trackId !in keptIds }
    return kept + shuffleNewTracks(appended)
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

internal fun visibleMusicTrackList(
    tracks: List<MusicListTrack>,
    isNetworkOnline: Boolean,
): List<MusicListTrack> = MusicLibraryVisibilityRules.visibleInLibrary(
    tracks = tracks,
    isDownloaded = { it.isDownloaded },
    isNetworkOnline = isNetworkOnline,
)
