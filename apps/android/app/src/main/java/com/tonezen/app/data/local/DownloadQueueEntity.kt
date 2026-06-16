package com.tonezen.app.data.local

import androidx.room.Entity

@Entity(
    tableName = "download_queue",
    primaryKeys = ["bookId", "trackId"],
)
data class DownloadQueueEntity(
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
