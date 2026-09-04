package com.tonezen.app.data.local

import com.tonezen.app.domain.downloads.DownloadQueueEntry

internal fun DownloadQueueEntity.toDomain() = DownloadQueueEntry(
    bookId = bookId,
    trackId = trackId,
    priority = priority,
    batchId = batchId,
    enqueuedAt = enqueuedAt,
    title = title,
    subtitle = subtitle,
    contentType = contentType,
    status = status,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    tempPath = tempPath,
)

internal fun DownloadQueueEntry.toEntity() = DownloadQueueEntity(
    bookId = bookId,
    trackId = trackId,
    priority = priority,
    batchId = batchId,
    enqueuedAt = enqueuedAt,
    title = title,
    subtitle = subtitle,
    contentType = contentType,
    status = status,
    bytesDownloaded = bytesDownloaded,
    totalBytes = totalBytes,
    tempPath = tempPath,
)
