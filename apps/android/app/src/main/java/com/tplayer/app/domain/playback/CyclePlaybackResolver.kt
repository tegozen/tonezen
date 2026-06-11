package com.tplayer.app.domain.playback

import com.tplayer.app.domain.model.Book
import com.tplayer.app.domain.model.ContentType
import com.tplayer.app.domain.model.Cycle
import com.tplayer.app.domain.model.Track

data class NextPlaybackTarget(
    val track: Track?,
    val book: Book?,
    val isNextBookInCycle: Boolean,
)

class CyclePlaybackResolver {
    fun nextInBook(currentTrack: Track, tracks: List<Track>): Track? {
        val sorted = tracks.sortedBy { it.sortOrder }
        val index = sorted.indexOfFirst { it.id == currentTrack.id }
        if (index < 0 || index >= sorted.lastIndex) return null
        return sorted[index + 1]
    }

    fun previousInBook(currentTrack: Track, tracks: List<Track>): Track? {
        val sorted = tracks.sortedBy { it.sortOrder }
        val index = sorted.indexOfFirst { it.id == currentTrack.id }
        if (index <= 0) return null
        return sorted[index - 1]
    }

    fun nextInCycle(
        currentBook: Book,
        currentTrack: Track,
        cycle: Cycle,
        booksBySlug: Map<String, Book>,
        tracksByBookId: Map<String, List<Track>>,
    ): NextPlaybackTarget {
        val bookTracks = tracksByBookId[currentBook.id].orEmpty().sortedBy { it.sortOrder }
        val nextTrack = nextInBook(currentTrack, bookTracks)
        if (nextTrack != null) {
            return NextPlaybackTarget(nextTrack, currentBook, false)
        }
        if (currentBook.contentType != ContentType.AUDIOBOOK) {
            return NextPlaybackTarget(null, null, false)
        }
        val order = cycle.bookOrder
        val bookIndex = order.indexOf(currentBook.slug)
        if (bookIndex < 0 || bookIndex >= order.lastIndex) {
            return NextPlaybackTarget(null, null, false)
        }
        val nextBookSlug = order[bookIndex + 1]
        val nextBook = booksBySlug[nextBookSlug] ?: return NextPlaybackTarget(null, null, false)
        val nextBookTracks = tracksByBookId[nextBook.id].orEmpty().sortedBy { it.sortOrder }
        val firstTrack = nextBookTracks.firstOrNull()
        return NextPlaybackTarget(firstTrack, nextBook, firstTrack != null)
    }
}
