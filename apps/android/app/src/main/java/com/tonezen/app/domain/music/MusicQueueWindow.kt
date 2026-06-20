package com.tonezen.app.domain.music

object MusicQueueWindow {
    const val INITIAL_WINDOW_SIZE = 24
    const val APPEND_WINDOW_SIZE = 12
    const val APPEND_TRIGGER_REMAINING = 4

    fun <T> initialWindow(
        items: List<T>,
        startTrackId: String,
        idOf: (T) -> String,
        size: Int = INITIAL_WINDOW_SIZE,
    ): List<T> {
        if (items.isEmpty() || size <= 0) return emptyList()
        val startIndex = items.indexOfFirst { idOf(it) == startTrackId }.takeIf { it >= 0 } ?: 0
        return collectWindow(
            items = items,
            startIndex = startIndex,
            excludedIds = emptySet(),
            idOf = idOf,
            size = size,
        )
    }

    fun <T> appendWindow(
        items: List<T>,
        lastMaterializedTrackId: String,
        materializedTrackIds: Set<String>,
        idOf: (T) -> String,
        size: Int = APPEND_WINDOW_SIZE,
    ): List<T> {
        if (items.isEmpty() || size <= 0 || materializedTrackIds.size >= items.size) return emptyList()
        val tailIndex = items.indexOfFirst { idOf(it) == lastMaterializedTrackId }
        if (tailIndex < 0) return emptyList()
        return collectWindow(
            items = items,
            startIndex = (tailIndex + 1) % items.size,
            excludedIds = materializedTrackIds,
            idOf = idOf,
            size = size,
        )
    }

    fun shouldAppend(
        currentIndex: Int,
        queueSize: Int,
        remainingThreshold: Int = APPEND_TRIGGER_REMAINING,
    ): Boolean {
        if (queueSize <= 0 || currentIndex < 0 || currentIndex >= queueSize) return false
        return queueSize - currentIndex - 1 <= remainingThreshold
    }

    private fun <T> collectWindow(
        items: List<T>,
        startIndex: Int,
        excludedIds: Set<String>,
        idOf: (T) -> String,
        size: Int,
    ): List<T> {
        val result = mutableListOf<T>()
        var index = startIndex
        repeat(items.size) {
            val item = items[index]
            if (idOf(item) !in excludedIds) {
                result += item
                if (result.size == size) return result
            }
            index = (index + 1) % items.size
        }
        return result
    }
}
