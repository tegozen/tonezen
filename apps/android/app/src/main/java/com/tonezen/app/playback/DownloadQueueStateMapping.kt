package com.tonezen.app.playback

const val MUSIC_DOWNLOAD_CONTENT_TYPE = "music"

fun DownloadQueueState.forMusic(): DownloadQueueState {
    val musicQueued = queuedItems.filter { it.contentType == MUSIC_DOWNLOAD_CONTENT_TYPE }
    val musicHistory = completedHistory.filter { it.contentType == MUSIC_DOWNLOAD_CONTENT_TYPE }
    val activeIsMusic = activeTrackId != null &&
        activeBookId != null &&
        queuedItems.any { item ->
            item.trackId == activeTrackId &&
                item.bookId == activeBookId &&
                item.contentType == MUSIC_DOWNLOAD_CONTENT_TYPE
        }
    val musicBatchActive = activeBatchId != null &&
        (musicQueued + musicHistory).any { it.batchId == activeBatchId }

    return copy(
        queuedItems = musicQueued,
        completedHistory = musicHistory,
        activeBookId = if (activeIsMusic) activeBookId else null,
        activeTrackId = if (activeIsMusic) activeTrackId else null,
        activeProgress = if (activeIsMusic) activeProgress else null,
        bulkTotal = if (musicBatchActive) bulkTotal else 0,
        bulkDownloaded = if (musicBatchActive) bulkDownloaded else 0,
        activeBatchId = if (musicBatchActive) activeBatchId else null,
    )
}

fun DownloadQueueState.toMusicDownloadState(): MusicDownloadState {
    val musicQueue = forMusic()
    return MusicDownloadState(
        activeTrackId = musicQueue.activeTrackId,
        trackProgress = musicQueue.activeProgress,
        bulkDownloaded = musicQueue.bulkDownloaded,
        bulkTotal = musicQueue.bulkTotal,
    )
}

fun DownloadQueueState.queuedTrackIds(): Set<String> =
    queuedItems
        .filter {
            it.status == DownloadQueueItemStatus.QUEUED ||
                it.status == DownloadQueueItemStatus.PAUSED_OFFLINE
        }
        .map { it.trackId }
        .toSet()

fun DownloadQueueState.queuedMusicTrackIds(): Set<String> =
    forMusic().queuedTrackIds()
