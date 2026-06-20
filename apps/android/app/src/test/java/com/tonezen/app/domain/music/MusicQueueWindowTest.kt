package com.tonezen.app.domain.music

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicQueueWindowTest {
    private val book = Book("lib-1", "music-library", ContentType.MUSIC, "Music", "Artist")

    @Test
    fun `initial window starts at selected track and caps size`() {
        val window = MusicQueueWindow.initialWindow(
            items = tracks(30),
            startTrackId = "t10",
            idOf = { it.track.id },
        )

        assertEquals(MusicQueueWindow.INITIAL_WINDOW_SIZE, window.size)
        assertEquals("t10", window.first().track.id)
        assertEquals("t3", window.last().track.id)
    }

    @Test
    fun `initial window preserves order and wraps`() {
        val window = MusicQueueWindow.initialWindow(
            items = tracks(6),
            startTrackId = "t4",
            idOf = { it.track.id },
        )

        assertEquals(listOf("t4", "t5", "t0", "t1", "t2", "t3"), window.map { it.track.id })
    }

    @Test
    fun `append window starts after queue tail and skips materialized tracks`() {
        val entries = tracks(40)
        val materializedIds = (10..33).map { "t$it" }.toSet()
        val window = MusicQueueWindow.appendWindow(
            items = entries,
            lastMaterializedTrackId = "t33",
            materializedTrackIds = materializedIds,
            idOf = { it.track.id },
        )

        assertEquals(MusicQueueWindow.APPEND_WINDOW_SIZE, window.size)
        assertEquals((34..39).map { "t$it" } + (0..5).map { "t$it" }, window.map { it.track.id })
        assertTrue(window.none { it.track.id in materializedIds })
    }

    @Test
    fun `append trigger fires near materialized queue end`() {
        assertTrue(MusicQueueWindow.shouldAppend(currentIndex = 19, queueSize = 24))
        assertEquals(false, MusicQueueWindow.shouldAppend(currentIndex = 18, queueSize = 24))
    }

    private fun tracks(count: Int) = (0 until count).map { index ->
        MusicLibraryTrack(
            book = book,
            track = Track(
                id = "t$index",
                bookId = book.id,
                sortOrder = index,
                title = "Track $index",
                filename = "track-$index.mp3",
                durationMs = 60_000L,
                localPath = null,
            ),
        )
    }
}
