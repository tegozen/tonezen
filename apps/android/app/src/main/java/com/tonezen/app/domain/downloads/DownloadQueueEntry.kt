package com.tonezen.app.domain.downloads

data class DownloadQueueEntry(
    val bookId: String,
    val trackId: String,
    val priority: String,
    val batchId: String?,
    val enqueuedAt: Long,
    val title: String,
    val subtitle: String?,
    val contentType: String,
    val status: String,
    val bytesDownloaded: Long,
    val totalBytes: Long?,
    val tempPath: String?,
)
