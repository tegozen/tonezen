package com.tonezen.app.playback

fun DownloadQueueState.toMusicDownloadState(): MusicDownloadState =
    MusicDownloadState(
        activeTrackId = activeTrackId,
        trackProgress = activeProgress,
        bulkDownloaded = bulkDownloaded,
        bulkTotal = bulkTotal,
    )

fun DownloadQueueState.queuedTrackIds(): Set<String> =
    queuedItems
        .filter {
            it.status == DownloadQueueItemStatus.QUEUED ||
                it.status == DownloadQueueItemStatus.PAUSED_OFFLINE
        }
        .map { it.trackId }
        .toSet()
