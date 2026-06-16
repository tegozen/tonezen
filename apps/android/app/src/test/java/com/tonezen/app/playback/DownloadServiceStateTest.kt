package com.tonezen.app.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadServiceStateTest {
    @Test
    fun shouldKeepDownloadServiceForeground_returnsFalseForIdleQueue() {
        assertFalse(shouldKeepDownloadServiceForeground(DownloadQueueState()))
    }

    @Test
    fun shouldKeepDownloadServiceForeground_returnsTrueForQueuedItems() {
        assertTrue(
            shouldKeepDownloadServiceForeground(
                DownloadQueueState(
                    queuedItems = listOf(
                        DownloadQueueItem(
                            bookId = "book-1",
                            trackId = "track-1",
                            title = "Track",
                            subtitle = null,
                            contentType = "music",
                            status = DownloadQueueItemStatus.QUEUED,
                            progress = null,
                            batchId = null,
                            enqueuedAt = 1L,
                            completedAt = null,
                        ),
                    ),
                ),
            ),
        )
    }
}
