package com.tonezen.app.domain.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueuePolicyTest {
    private val keyA = DownloadQueueKey("b1", "t1")
    private val keyB = DownloadQueueKey("b1", "t2")

    @Test
    fun sortPendingOrdersByPriorityThenEnqueueTime() {
        val sorted = DownloadQueuePolicy.sortPending(
            listOf(
                DownloadQueueSortable(keyB, DownloadPriority.BULK, 2L),
                DownloadQueueSortable(keyA, DownloadPriority.PLAY, 3L),
                DownloadQueueSortable(keyB, DownloadPriority.USER, 1L),
            ),
        )
        assertEquals(keyA, sorted[0].key)
        assertEquals(keyB, sorted[1].key)
        assertEquals(keyB, sorted[2].key)
    }

    @Test
    fun mergePriorityKeepsHigher() {
        assertEquals(
            DownloadPriority.PLAY,
            DownloadQueuePolicy.mergePriority(DownloadPriority.USER, DownloadPriority.PLAY),
        )
        assertEquals(
            DownloadPriority.BULK,
            DownloadQueuePolicy.mergePriority(DownloadPriority.BULK, DownloadPriority.PREFETCH),
        )
    }

    @Test
    fun shouldUpgradeOnlyWhenIncomingHigher() {
        assertTrue(DownloadQueuePolicy.shouldUpgrade(DownloadPriority.PREFETCH, DownloadPriority.USER))
        assertFalse(DownloadQueuePolicy.shouldUpgrade(DownloadPriority.PLAY, DownloadPriority.BULK))
    }
}
