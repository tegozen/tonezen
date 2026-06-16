package com.tonezen.app.domain.downloads

enum class DownloadAwaitResult {
    COMPLETED,
    CANCELLED,
    FAILED,
    OFFLINE,
}

data class EnqueueDownloadRequest(
    val bookId: String,
    val trackId: String,
    val priority: DownloadPriority,
    val batchId: String? = null,
    val title: String,
    val subtitle: String?,
    val contentType: String,
    val enqueuedAt: Long = System.currentTimeMillis(),
)
