package com.tonezen.app.ui.player

import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.playback.PlaybackSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BookDetailPlaybackStateTest {
    private val tracks = listOf(
        track("track-1", 0),
        track("track-2", 1),
    )

    @Test
    fun snapshotForPausedBookTrack_keepsLivePositionForDetailControls() {
        val state = resolveBookDetailPlaybackState(
            tracks = tracks,
            snapshot = PlaybackSnapshot(
                isPlaying = false,
                positionMs = 42_000L,
                durationMs = 120_000L,
                trackId = "track-2",
                contentType = ContentType.AUDIOBOOK,
            ),
        )

        assertEquals("track-2", state.activeTrackId)
        assertEquals(42_000L, state.positionMs)
        assertEquals(120_000L, state.durationMs)
        assertFalse(state.isPlaying)
        assertTrue(state.isActiveForBook)
    }

    @Test
    fun snapshotForAnotherBook_clearsDetailPlaybackState() {
        val state = resolveBookDetailPlaybackState(
            tracks = tracks,
            snapshot = PlaybackSnapshot(
                isPlaying = true,
                positionMs = 42_000L,
                durationMs = 120_000L,
                trackId = "other-track",
                contentType = ContentType.AUDIOBOOK,
            ),
        )

        assertNull(state.activeTrackId)
        assertEquals(0L, state.positionMs)
        assertEquals(0L, state.durationMs)
        assertFalse(state.isPlaying)
        assertFalse(state.isActiveForBook)
    }

    @Test
    fun bookDetailTracksForDisplay_ordersBySortOrder() {
        val displayTracks = bookDetailTracksForDisplay(
            listOf(
                track("track-015", 14),
                track("track-001", 0),
                track("track-002", 1),
            ),
        )

        assertEquals(listOf("track-001", "track-002", "track-015"), displayTracks.map { it.id })
    }

    private fun track(id: String, sortOrder: Int) = Track(
        id = id,
        bookId = "book-1",
        sortOrder = sortOrder,
        title = "Track $sortOrder",
        filename = "$id.mp3",
        durationMs = 120_000L,
        localPath = null,
    )
}
