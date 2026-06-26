package com.tonezen.app.domain.downloads

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueBookIdPolicyTest {
    @Test
    fun resolveEnqueueBookId_prefersCatalogBookId() {
        assertEquals(
            "catalog-book",
            DownloadQueueBookIdPolicy.resolveEnqueueBookId("stale-book", "catalog-book"),
        )
    }

    @Test
    fun resolveEnqueueBookId_fallsBackToRequestedWhenCatalogMissing() {
        assertEquals(
            "requested-book",
            DownloadQueueBookIdPolicy.resolveEnqueueBookId("requested-book", null),
        )
    }

    @Test
    fun isStaleQueueEntry_detectsMismatchedBookId() {
        assertTrue(DownloadQueueBookIdPolicy.isStaleQueueEntry("old-book", "new-book"))
        assertFalse(DownloadQueueBookIdPolicy.isStaleQueueEntry("same-book", "same-book"))
        assertFalse(DownloadQueueBookIdPolicy.isStaleQueueEntry("any-book", null))
    }
}
