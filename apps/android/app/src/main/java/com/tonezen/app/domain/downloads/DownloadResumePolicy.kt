package com.tonezen.app.domain.downloads

object DownloadResumePolicy {
    const val COMPLETED_HISTORY_LIMIT = 200

    enum class ResumeAction {
        FULL_DOWNLOAD,
        RANGE_APPEND,
        RESTART,
    }

    fun resolveResumeAction(
        partFileLength: Long,
        bytesDownloaded: Long,
        totalBytes: Long?,
        rangeResponseCode: Int?,
    ): ResumeAction {
        if (partFileLength <= 0L && bytesDownloaded <= 0L) return ResumeAction.FULL_DOWNLOAD
        if (totalBytes != null && totalBytes > 0 && partFileLength > totalBytes) return ResumeAction.RESTART
        if (partFileLength != bytesDownloaded && bytesDownloaded > 0) {
            return if (partFileLength > bytesDownloaded) ResumeAction.RESTART else ResumeAction.RANGE_APPEND
        }
        return when (rangeResponseCode) {
            206 -> ResumeAction.RANGE_APPEND
            200, 416 -> ResumeAction.RESTART
            null -> if (partFileLength > 0L) ResumeAction.RANGE_APPEND else ResumeAction.FULL_DOWNLOAD
            else -> ResumeAction.RESTART
        }
    }

    fun progressFraction(bytesDownloaded: Long, totalBytes: Long?): Float? {
        if (totalBytes == null || totalBytes <= 0L) return null
        return (bytesDownloaded.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f)
    }
}
