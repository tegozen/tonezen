package com.tonezen.app.domain.downloads

object DownloadQueueBookIdPolicy {
    fun resolveEnqueueBookId(requestedBookId: String, catalogBookId: String?): String =
        catalogBookId?.takeIf { it.isNotBlank() } ?: requestedBookId

    fun isStaleQueueEntry(entryBookId: String, catalogBookId: String?): Boolean =
        catalogBookId != null && entryBookId != catalogBookId
}
