package com.tonezen.app.domain.music

object MusicPlaybackAdvanceRules {
    fun <T> findNextPlayable(
        items: List<T>,
        currentIndex: Int,
        isPlayable: (T) -> Boolean,
        nextIndex: (Int, Int) -> Int = MusicShuffleQueue::nextIndex,
    ): Int? {
        if (items.isEmpty() || currentIndex !in items.indices) return null
        var index = currentIndex
        repeat(items.size - 1) {
            index = nextIndex(index, items.size)
            if (index < 0) return null
            if (isPlayable(items[index])) return index
        }
        return null
    }

    fun <T> findPreviousPlayable(
        items: List<T>,
        currentIndex: Int,
        isPlayable: (T) -> Boolean,
        previousIndex: (Int, Int) -> Int = MusicShuffleQueue::previousIndex,
    ): Int? {
        if (items.isEmpty() || currentIndex !in items.indices) return null
        var index = currentIndex
        repeat(items.size - 1) {
            index = previousIndex(index, items.size)
            if (index < 0) return null
            if (isPlayable(items[index])) return index
        }
        return null
    }

    fun isTrackPlayable(isDownloaded: Boolean, isNetworkOnline: Boolean): Boolean =
        isDownloaded || isNetworkOnline
}
