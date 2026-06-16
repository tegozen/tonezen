package com.tonezen.app.domain.music

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicLibraryVisibilityRulesTest {
    private data class Track(val id: String, val downloaded: Boolean)

    @Test
    fun showsAllTracksWhenOnline() {
        val tracks = listOf(Track("a", true), Track("b", false))
        assertEquals(tracks, MusicLibraryVisibilityRules.visibleInLibrary(tracks, { it.downloaded }, true))
    }

    @Test
    fun showsOnlyDownloadedTracksWhenOffline() {
        val tracks = listOf(Track("a", true), Track("b", false), Track("c", true))
        val visible = MusicLibraryVisibilityRules.visibleInLibrary(tracks, { it.downloaded }, false)
        assertEquals(listOf(Track("a", true), Track("c", true)), visible)
    }
}
