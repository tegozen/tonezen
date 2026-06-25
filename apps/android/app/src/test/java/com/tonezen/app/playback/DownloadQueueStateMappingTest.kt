package com.tonezen.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueStateMappingTest {
    @Test
    fun forMusic_hidesAudiobookActiveDownload() {
        val state = DownloadQueueState(
            queuedItems = listOf(
                queueItem(
                    trackId = "chapter-1",
                    contentType = "audiobook",
                    status = DownloadQueueItemStatus.DOWNLOADING,
                    progress = 0.4f,
                ),
            ),
            activeBookId = "book-1",
            activeTrackId = "chapter-1",
            activeProgress = 0.4f,
            bulkTotal = 12,
            bulkDownloaded = 3,
            activeBatchId = "audiobook-batch",
        )

        val music = state.forMusic()

        assertNull(music.activeTrackId)
        assertNull(music.activeProgress)
        assertEquals(0, music.bulkTotal)
        assertEquals(0, music.bulkDownloaded)
        assertNull(music.activeBatchId)
        assertTrue(music.queuedItems.isEmpty())
    }

    @Test
    fun forMusic_keepsMusicActiveDownloadAndBulk() {
        val state = DownloadQueueState(
            queuedItems = listOf(
                queueItem(
                    trackId = "track-1",
                    contentType = MUSIC_DOWNLOAD_CONTENT_TYPE,
                    status = DownloadQueueItemStatus.DOWNLOADING,
                    progress = 0.6f,
                    batchId = "music-batch",
                    bookId = "album-1",
                ),
                queueItem(
                    trackId = "chapter-1",
                    contentType = "audiobook",
                    status = DownloadQueueItemStatus.QUEUED,
                ),
            ),
            activeBookId = "album-1",
            activeTrackId = "track-1",
            activeProgress = 0.6f,
            bulkTotal = 5,
            bulkDownloaded = 1,
            activeBatchId = "music-batch",
        )

        val music = state.forMusic()

        assertEquals("track-1", music.activeTrackId)
        assertEquals(0.6f, music.activeProgress)
        assertEquals(5, music.bulkTotal)
        assertEquals(1, music.bulkDownloaded)
        assertEquals("music-batch", music.activeBatchId)
        assertEquals(1, music.queuedItems.size)
        assertEquals("track-1", music.queuedItems.single().trackId)
    }

    @Test
    fun forMusic_keepsActiveMusicDownloadWhileQueuedStatus() {
        val state = DownloadQueueState(
            queuedItems = listOf(
                queueItem(
                    trackId = "track-1",
                    contentType = MUSIC_DOWNLOAD_CONTENT_TYPE,
                    status = DownloadQueueItemStatus.QUEUED,
                    batchId = "music-batch",
                    bookId = "album-1",
                ),
            ),
            activeBookId = "album-1",
            activeTrackId = "track-1",
            activeProgress = 0.35f,
            bulkTotal = 3,
            bulkDownloaded = 0,
            activeBatchId = "music-batch",
        )

        val music = state.forMusic()

        assertEquals("track-1", music.activeTrackId)
        assertEquals(0.35f, music.activeProgress)
    }

    @Test
    fun toMusicDownloadState_usesMusicOnlyQueue() {
        val state = DownloadQueueState(
            queuedItems = listOf(
                queueItem(
                    trackId = "chapter-1",
                    contentType = "audiobook",
                    status = DownloadQueueItemStatus.DOWNLOADING,
                    progress = 0.2f,
                ),
            ),
            activeBookId = "book-1",
            activeTrackId = "chapter-1",
            activeProgress = 0.2f,
            bulkTotal = 8,
            bulkDownloaded = 2,
            activeBatchId = "audiobook-batch",
        )

        val music = state.toMusicDownloadState()

        assertNull(music.activeTrackId)
        assertNull(music.trackProgress)
        assertEquals(0, music.bulkTotal)
        assertFalse(music.isBulkDownloading)
    }

    private fun queueItem(
        trackId: String,
        contentType: String,
        status: DownloadQueueItemStatus,
        progress: Float? = null,
        batchId: String? = null,
        bookId: String = "book-$trackId",
    ) = DownloadQueueItem(
        bookId = bookId,
        trackId = trackId,
        title = trackId,
        subtitle = null,
        contentType = contentType,
        status = status,
        progress = progress,
        batchId = batchId,
        enqueuedAt = 1L,
        completedAt = null,
    )
}
