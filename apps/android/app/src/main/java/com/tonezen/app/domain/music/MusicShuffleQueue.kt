package com.tonezen.app.domain.music

object MusicShuffleQueue {
    fun order(
        entries: List<MusicLibraryTrack>,
        startTrackId: String,
    ): List<MusicLibraryTrack> {
        if (entries.isEmpty()) return emptyList()
        if (entries.size == 1) return entries
        val start = entries.find { it.track.id == startTrackId } ?: entries.first()
        val rest = entries.filter { it.track.id != startTrackId }.shuffled()
        return listOf(start) + rest
    }

    fun nextIndex(currentIndex: Int, size: Int): Int =
        if (size <= 0) -1 else (currentIndex + 1) % size

    fun previousIndex(currentIndex: Int, size: Int): Int =
        if (size <= 0) -1 else (currentIndex - 1 + size) % size
}
