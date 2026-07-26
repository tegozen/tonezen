package com.tonezen.app.ui.music

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicLibraryVisibilityRules
import com.tonezen.app.domain.music.MusicTrackListMerge

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

internal fun buildMusicTrackListFromCandidates(
    candidates: List<Pair<Book, Track>>,
    shuffle: Boolean,
    downloadedTrackIds: Set<String>,
): List<MusicListTrack> {
    if (candidates.isEmpty()) return emptyList()
    val ordered = if (shuffle) candidates.shuffled() else candidates
    return ordered.map { (book, track) -> toMusicListTrack(book, track, downloadedTrackIds) }
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
    return MusicTrackListMerge.merge(
        existing = existing,
        built = built,
        musicStartedInSession = musicStartedInSession,
        idOf = { it.trackId },
        metadataEquals = { a, b ->
            a.trackTitle == b.trackTitle &&
                a.artist == b.artist &&
                a.albumTitle == b.albumTitle &&
                a.bookId == b.bookId &&
                a.durationMs == b.durationMs
        },
        shuffleInitial = { it.shuffled() },
        shuffleAppended = shuffleNewTracks,
        refreshExisting = { refreshMusicTrackListDownloadState(it, downloadedTrackIds) },
    )
}

internal fun visibleMusicTrackList(
    tracks: List<MusicListTrack>,
    isNetworkOnline: Boolean,
): List<MusicListTrack> = MusicLibraryVisibilityRules.visibleInLibrary(
    tracks = tracks,
    isDownloaded = { it.isDownloaded },
    isNetworkOnline = isNetworkOnline,
)
