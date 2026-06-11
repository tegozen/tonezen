package com.tplayer.app.domain.playback

import com.tplayer.app.domain.model.Book
import com.tplayer.app.domain.model.Cycle
import com.tplayer.app.domain.model.Track

class PlaybackCoordinator(
    private val cycleResolver: CyclePlaybackResolver = CyclePlaybackResolver(),
) {
    fun resolveAutoAdvance(
        currentBook: Book,
        currentTrack: Track,
        cycle: Cycle?,
        booksBySlug: Map<String, Book>,
        tracksByBookId: Map<String, List<Track>>,
    ): NextPlaybackTarget {
        val bookTracks = tracksByBookId[currentBook.id].orEmpty()
        val nextInSameBook = cycleResolver.nextInBook(currentTrack, bookTracks)
        if (nextInSameBook != null) {
            return NextPlaybackTarget(nextInSameBook, currentBook, false)
        }
        if (cycle == null) {
            return NextPlaybackTarget(null, null, false)
        }
        return cycleResolver.nextInCycle(
            currentBook,
            currentTrack,
            cycle,
            booksBySlug,
            tracksByBookId,
        )
    }
}
