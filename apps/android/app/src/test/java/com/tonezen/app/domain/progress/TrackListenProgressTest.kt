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

    @Test
    fun `completed chapter playback restarts from beginning`() {
        val progress = progress("ch2", 96_000)
        val startMs = resolveAudiobookPlaybackStartMs(progress, tracks[1])
        assertEquals(0L, startMs)
    }

    @Test
    fun `in progress chapter playback resumes saved position`() {
        val progress = progress("ch2", 50_000)
        val startMs = resolveAudiobookPlaybackStartMs(progress, tracks[1])
        assertEquals(50_000L, startMs)
    }

    @Test
    fun `book is fully listened when last chapter is completed`() {
        val progress = progress("ch3", 100_000)
        assertEquals(true, isBookFullyListened(tracks, progress))
    }

    @Test
    fun `book is not fully listened without progress`() {
        assertEquals(false, isBookFullyListened(tracks, null))
    }

    @Test
    fun `same chapter resumes saved position`() {
        val progress = progress("ch2", 50_000)
        val intent = resolveAudiobookPlaybackIntent(tracks, progress, tracks[1])
        assertEquals(AudiobookPlaybackIntent.Resume(50_000L), intent)
    }

    @Test
    fun `later chapter starts from zero`() {
        val progress = progress("ch2", 50_000)
        val intent = resolveAudiobookPlaybackIntent(tracks, progress, tracks[2])
        assertEquals(AudiobookPlaybackIntent.StartFromZero, intent)
    }

    @Test
    fun `earlier chapter requires confirmation`() {
        val progress = progress("ch3", 10_000)
        val intent = resolveAudiobookPlaybackIntent(tracks, progress, tracks[0])
        assertEquals(
            AudiobookPlaybackIntent.ConfirmEarlierChapter("ch3", 10_000L),
            intent,
        )
    }

    @Test
    fun `no progress starts from zero`() {
        val intent = resolveAudiobookPlaybackIntent(tracks, null, tracks[1])
        assertEquals(AudiobookPlaybackIntent.StartFromZero, intent)
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
