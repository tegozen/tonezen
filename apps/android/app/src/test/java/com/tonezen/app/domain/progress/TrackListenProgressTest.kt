package com.tonezen.app.domain.progress

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TrackListenProgressTest {
    private val tracks = listOf(
        track("ch1", 0, 100_000),
        track("ch2", 1, 100_000),
        track("ch3", 2, 100_000),
    )

    @Test
    fun `earlier chapters are completed when progress is on later chapter`() {
        val progress = progress("ch3", 10_000)
        val state = resolveTrackListenState(tracks, progress, "ch1")
        assertEquals(TrackListenStatus.COMPLETED, state.status)
        assertEquals(1f, state.barFraction)
    }

    @Test
    fun `later chapters are not started`() {
        val progress = progress("ch2", 50_000)
        val state = resolveTrackListenState(tracks, progress, "ch3")
        assertEquals(TrackListenStatus.NOT_STARTED, state.status)
        assertNull(state.barFraction)
    }

    @Test
    fun `current chapter shows partial progress`() {
        val progress = progress("ch2", 50_000)
        val state = resolveTrackListenState(tracks, progress, "ch2")
        assertEquals(TrackListenStatus.IN_PROGRESS, state.status)
        assertEquals(0.5f, state.barFraction)
    }

    @Test
    fun `current chapter is completed near end`() {
        val progress = progress("ch2", 96_000)
        val state = resolveTrackListenState(tracks, progress, "ch2")
        assertEquals(TrackListenStatus.COMPLETED, state.status)
        assertEquals(1f, state.barFraction)
    }

    @Test
    fun `live position overrides saved progress for active chapter`() {
        val progress = progress("ch2", 20_000)
        val state = resolveTrackListenState(tracks, progress, "ch2", livePositionMs = 70_000)
        assertEquals(TrackListenStatus.IN_PROGRESS, state.status)
        assertEquals(0.7f, state.barFraction)
    }

    private fun track(id: String, sortOrder: Int, durationMs: Long) = Track(
        id = id,
        bookId = "book-1",
        sortOrder = sortOrder,
        title = id,
        filename = "$id.mp3",
        durationMs = durationMs,
        localPath = null,
    )

    private fun progress(trackId: String, positionMs: Long) = AudiobookProgress(
        bookId = "book-1",
        trackId = trackId,
        positionMs = positionMs,
        updatedAtEpochMs = 1L,
    )
}
