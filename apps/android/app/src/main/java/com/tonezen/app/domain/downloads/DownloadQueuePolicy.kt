package com.tonezen.app.domain.downloads

data class DownloadQueueKey(val bookId: String, val trackId: String)

data class DownloadQueueSortable(
    val key: DownloadQueueKey,
    val priority: DownloadPriority,
    val enqueuedAt: Long,
)

object DownloadQueuePolicy {
    fun sortPending(items: List<DownloadQueueSortable>): List<DownloadQueueSortable> =
        items.sortedWith(
            compareByDescending<DownloadQueueSortable> { it.priority.weight }
                .thenBy { it.enqueuedAt },
        )

    fun mergePriority(current: DownloadPriority, incoming: DownloadPriority): DownloadPriority =
        if (incoming.higherThan(current)) incoming else current

    fun shouldUpgrade(current: DownloadPriority, incoming: DownloadPriority): Boolean =
        incoming.higherThan(current)
}
