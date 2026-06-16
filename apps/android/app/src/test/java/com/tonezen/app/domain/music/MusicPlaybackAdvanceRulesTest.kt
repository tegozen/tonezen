package com.tonezen.app.domain.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicPlaybackAdvanceRulesTest {
    private data class Item(val id: String, val downloaded: Boolean)

    @Test
    fun findNextPlayableSkipsUnavailableWhenOffline() {
        val items = listOf(
            Item("a", true),
            Item("b", false),
            Item("c", true),
        )
        val next = MusicPlaybackAdvanceRules.findNextPlayable(
            items = items,
            currentIndex = 0,
            isPlayable = { it.downloaded },
        )
        assertEquals(2, next)
    }

    @Test
    fun findNextPlayableReturnsNullWhenNoPlayableTrack() {
        val items = listOf(Item("a", true), Item("b", false))
        val next = MusicPlaybackAdvanceRules.findNextPlayable(
            items = items,
            currentIndex = 0,
            isPlayable = { it.downloaded },
        )
        assertNull(next)
    }

    @Test
    fun findPreviousPlayableSkipsUnavailableWhenOffline() {
        val items = listOf(
            Item("a", true),
            Item("b", false),
            Item("c", true),
        )
        val previous = MusicPlaybackAdvanceRules.findPreviousPlayable(
            items = items,
            currentIndex = 0,
            isPlayable = { it.downloaded },
        )
        assertEquals(2, previous)
    }

    @Test
    fun isTrackPlayableRequiresDownloadWhenOffline() {
        assertTrue(MusicPlaybackAdvanceRules.isTrackPlayable(isDownloaded = true, isNetworkOnline = false))
        assertFalse(MusicPlaybackAdvanceRules.isTrackPlayable(isDownloaded = false, isNetworkOnline = false))
        assertTrue(MusicPlaybackAdvanceRules.isTrackPlayable(isDownloaded = false, isNetworkOnline = true))
    }
}
