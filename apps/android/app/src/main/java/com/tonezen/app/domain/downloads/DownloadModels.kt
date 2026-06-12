package com.tonezen.app.domain.downloads

data class DownloadedBookSummary(
    val bookId: String,
    val title: String,
    val author: String?,
    val contentType: String,
    val downloadedTracks: Int,
    val totalTracks: Int,
    val sizeBytes: Long,
    val downloadProgress: Float?,
)

data class StorageStats(
    val usedBytes: Long,
    val totalBytes: Long?,
) {
    val usedPercent: Float?
        get() = totalBytes?.takeIf { it > 0L }?.let { usedBytes.toFloat() / it.toFloat() }
}
