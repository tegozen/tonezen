package com.tonezen.app.domain.progress

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BookListenProgressTest {
    private val bookId = "b1"
    private val tracks = listOf(
        track("t1", 0, "One", 100_000),
        track("t2", 1, "Two", 100_000),
    )

    @Test
    fun `can continue returns resume info for partial chapter`() {
        val state = canContinueBookListening(
            bookId = bookId,
            tracks = tracks,
            progress = progress("t1", 5_000),
        )
        assertEquals(BookContinueState("One", 5_000), state)
    }

    @Test
    fun `can continue returns null when book fully listened`() {
        assertNull(
            canContinueBookListening(
                bookId = bookId,
                tracks = tracks,
                progress = progress("t2", 95_000),
            ),
        )
    }

    @Test
    fun `can continue returns null without saved progress`() {
        assertNull(canContinueBookListening(bookId, tracks, null))
    }

    @Test
    fun `can continue returns null when progress belongs to another book`() {
        assertNull(
            canContinueBookListening(
                bookId = bookId,
                tracks = tracks,
                progress = AudiobookProgress(
                    bookId = "other",
                    trackId = "t1",
                    positionMs = 5_000,
                    updatedAtEpochMs = 1L,
                ),
            ),
        )
    }

    @Test
    fun `build book track progress uses live position for active track`() {
        val map = buildBookTrackProgress(tracks, "t1", 5_000, "t1", 5_000)
        assertEquals(0.05f, map["t1"])
    }

    @Test
    fun `build book track progress marks earlier tracks complete`() {
        val map = buildBookTrackProgress(tracks, "t2", 0L, null, 0L)
        assertEquals(1f, map["t1"])
    }

    private fun track(id: String, sortOrder: Int, title: String, durationMs: Long) = Track(
        id = id,
        bookId = bookId,
        sortOrder = sortOrder,
        title = title,
        filename = "$id.mp3",
        durationMs = durationMs,
        localPath = null,
    )

    private fun progress(trackId: String, positionMs: Long) = AudiobookProgress(
        bookId = bookId,
        trackId = trackId,
        positionMs = positionMs,
        updatedAtEpochMs = 1L,
    )
}
