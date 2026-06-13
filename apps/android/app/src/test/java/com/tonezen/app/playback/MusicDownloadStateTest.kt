package com.tonezen.app.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicDownloadStateTest {
    @Test
    fun bulkProgressIncludesActiveTrackFraction() {
        val state = MusicDownloadState(
            bulkDownloaded = 1,
            bulkTotal = 4,
            activeTrackId = "t2",
            trackProgress = 0.5f,
        )
        assertEquals(0.375f, state.bulkProgress!!, 0.001f)
    }

    @Test
    fun bulkDownloadingStaysActiveWithoutPerTrackProgress() {
        val state = MusicDownloadState(bulkDownloaded = 1, bulkTotal = 7)
        assertTrue(state.isBulkDownloading)
        assertTrue(state.isActive)
        assertFalse(state.isTrackDownloading)
        assertNull(state.progressForTrack("t1"))
    }

    @Test
    fun incrementBulkClearsActiveTrackProgress() {
        val notifier = MusicDownloadNotifier()
        notifier.beginBulk(0, 3)
        notifier.updateBulk(0, 3, "t1", 0.8f)
        notifier.incrementBulkDownloaded(1, 3)

        val state = notifier.state.value
        assertEquals(1, state.bulkDownloaded)
        assertNull(state.activeTrackId)
        assertNull(state.trackProgress)
        assertTrue(state.isBulkDownloading)
    }

    @Test
    fun clearResetsBulkAndTrackState() {
        val notifier = MusicDownloadNotifier()
        notifier.beginBulk(1, 7)
        notifier.clear()
        val state = notifier.state.value
        assertFalse(state.isBulkDownloading)
        assertFalse(state.isActive)
        assertEquals(0, state.bulkDownloaded)
        assertEquals(0, state.bulkTotal)
    }
}
