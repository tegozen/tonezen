package com.tonezen.app.ui.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailListScrollTest {
    @Test
    fun bookDetailTrackListIndex_accountsForHeaderItems() {
        assertEquals(0, bookDetailTrackListIndex(trackIndex = 0, hasPlaybackControls = false, hasContinueButton = false))
        assertEquals(1, bookDetailTrackListIndex(trackIndex = 0, hasPlaybackControls = true, hasContinueButton = false))
        assertEquals(2, bookDetailTrackListIndex(trackIndex = 0, hasPlaybackControls = true, hasContinueButton = true))
        assertEquals(4, bookDetailTrackListIndex(trackIndex = 2, hasPlaybackControls = true, hasContinueButton = true))
    }

    @Test
    fun isLazyItemVisibleAboveBottomPadding_respectsMiniPlayerReserve() {
        assertTrue(
            isLazyItemVisibleAboveBottomPadding(
                itemOffset = 100,
                itemSize = 56,
                viewportStart = 0,
                viewportEnd = 600,
                bottomPaddingPx = 112,
            ),
        )
        assertFalse(
            isLazyItemVisibleAboveBottomPadding(
                itemOffset = 500,
                itemSize = 56,
                viewportStart = 0,
                viewportEnd = 600,
                bottomPaddingPx = 112,
            ),
        )
    }

    @Test
    fun lazyItemScrollOffsetAboveBottomPadding_alignsRowAboveBottomChrome() {
        assertEquals(432, lazyItemScrollOffsetAboveBottomPadding(viewportHeight = 600, itemSize = 56, bottomPaddingPx = 112))
        assertEquals(0, lazyItemScrollOffsetAboveBottomPadding(viewportHeight = 600, itemSize = 700, bottomPaddingPx = 112))
    }
}
