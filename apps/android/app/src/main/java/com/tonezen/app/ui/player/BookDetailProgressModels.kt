package com.tonezen.app.ui.player

/** Live transport progress for book detail controls — isolated from chapter list UiState. */
data class BookDetailPlaybackProgress(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/** Per-track download scalars for chapter rows (not the full queue state). */
data class BookDetailTrackDownloadUi(
    val progress: Float? = null,
    val isQueued: Boolean = false,
) {
    val isDownloading: Boolean
        get() = progress != null
}
