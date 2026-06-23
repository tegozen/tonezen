package com.tonezen.app.domain.downloads

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track

fun nextAudiobookDownloadRequest(
    book: Book,
    tracks: List<Track>,
    currentTrackId: String?,
    savedTrackId: String?,
): EnqueueDownloadRequest? {
    val track = nextAudiobookDownloadTrack(tracks, currentTrackId, savedTrackId) ?: return null
    return EnqueueDownloadRequest(
        bookId = book.id,
        trackId = track.id,
        priority = DownloadPriority.USER,
        title = track.title,
        subtitle = book.title,
        contentType = book.contentType.name.lowercase(),
    )
}

fun nextAudiobookDownloadTrack(
    tracks: List<Track>,
    currentTrackId: String?,
    savedTrackId: String?,
): Track? {
    val sorted = tracks.sortedBy { it.sortOrder }
    if (sorted.isEmpty()) return null

    val currentIndex = currentTrackId?.let { id -> sorted.indexOfFirst { it.id == id } } ?: -1
    if (currentIndex >= 0) {
        return sorted.drop(currentIndex + 1).firstOrNull { it.localPath.isNullOrBlank() }
    }

    val savedIndex = savedTrackId?.let { id -> sorted.indexOfFirst { it.id == id } } ?: -1
    val startIndex = if (savedIndex >= 0) savedIndex else 0
    return sorted.drop(startIndex).firstOrNull { it.localPath.isNullOrBlank() }
}
