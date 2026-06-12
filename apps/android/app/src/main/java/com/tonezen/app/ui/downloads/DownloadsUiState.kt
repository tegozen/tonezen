package com.tonezen.app.ui.downloads

import com.tonezen.app.domain.downloads.DownloadedBookSummary
import com.tonezen.app.domain.downloads.StorageStats

data class DownloadsUiState(
    val summaries: List<DownloadedBookSummary> = emptyList(),
    val storageStats: StorageStats = StorageStats(0L, null),
    val selectedTab: Int = 0,
    val showDeleteAllConfirm: Boolean = false,
    val error: String? = null,
)
