package com.tonezen.app.domain.music

object MusicShuffleQueue {
    fun order(
        entries: List<MusicLibraryTrack>,
        startTrackId: String,
    ): List<MusicLibraryTrack> {
        if (entries.isEmpty()) return emptyList()
        if (entries.size == 1) return entries
        val index = entries.indexOfFirst { it.track.id == startTrackId }
        if (index < 0) return entries
        return entries.drop(index) + entries.take(index)
    }

    fun nextIndex(currentIndex: Int, size: Int): Int =
        if (size <= 0) -1 else (currentIndex + 1) % size

    fun previousIndex(currentIndex: Int, size: Int): Int =
        if (size <= 0) -1 else (currentIndex - 1 + size) % size
}
