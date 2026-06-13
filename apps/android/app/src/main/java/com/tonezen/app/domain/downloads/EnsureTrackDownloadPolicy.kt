package com.tonezen.app.domain.downloads

object EnsureTrackDownloadPolicy {
    fun resolveLocalPathAfterDownload(
        downloadedPath: String,
        markSucceeded: Boolean,
        recoveredPath: String?,
    ): String? = when {
        markSucceeded -> downloadedPath
        recoveredPath != null -> recoveredPath
        else -> null
    }

    fun resolveLocalPathAfterFailure(recoveredPath: String?): String? = recoveredPath
}
