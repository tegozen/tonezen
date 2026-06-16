package com.tonezen.app.playback

import com.tonezen.app.domain.downloads.DownloadResumePolicy

enum class DownloadQueueItemStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    CANCELLED,
    FAILED,
    PAUSED_OFFLINE,
}

data class DownloadQueueItem(
    val bookId: String,
    val trackId: String,
    val title: String,
    val subtitle: String?,
    val contentType: String,
    val status: DownloadQueueItemStatus,
    val progress: Float?,
    val batchId: String?,
    val enqueuedAt: Long,
    val completedAt: Long?,
)

data class DownloadQueueState(
    val queuedItems: List<DownloadQueueItem> = emptyList(),
    val completedHistory: List<DownloadQueueItem> = emptyList(),
    val activeBookId: String? = null,
    val activeTrackId: String? = null,
    val activeProgress: Float? = null,
    val bulkDownloaded: Int = 0,
    val bulkTotal: Int = 0,
    val activeBatchId: String? = null,
    val pausedForNetwork: Boolean = false,
) {
    val isActive: Boolean
        get() = queuedItems.any {
            it.status == DownloadQueueItemStatus.QUEUED ||
                it.status == DownloadQueueItemStatus.DOWNLOADING ||
                it.status == DownloadQueueItemStatus.PAUSED_OFFLINE
        } || activeTrackId != null

    val isTrackDownloading: Boolean
        get() = activeTrackId != null && activeProgress != null

    val isBulkDownloading: Boolean
        get() = bulkTotal > 0 && bulkDownloaded < bulkTotal

    fun progressForTrack(trackId: String): Float? =
        if (activeTrackId == trackId) activeProgress else null

    fun isTrackQueued(trackId: String): Boolean =
        queuedItems.any {
            it.trackId == trackId &&
                (it.status == DownloadQueueItemStatus.QUEUED || it.status == DownloadQueueItemStatus.PAUSED_OFFLINE)
        }

    val bulkProgress: Float?
        get() {
            if (bulkTotal <= 0) return null
            if (activeTrackId != null && activeProgress != null) {
                return ((bulkDownloaded + activeProgress) / bulkTotal).coerceIn(0f, 1f)
            }
            return (bulkDownloaded.toFloat() / bulkTotal).coerceIn(0f, 1f)
        }
}

fun DownloadQueueState.trimHistory(): DownloadQueueState {
    val limit = DownloadResumePolicy.COMPLETED_HISTORY_LIMIT
    if (completedHistory.size <= limit) return this
    return copy(completedHistory = completedHistory.takeLast(limit))
}
