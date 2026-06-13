package com.tonezen.app.domain.progress

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CycleListenProgressTest {
    private val bookOne = book("book-1", "book-one", 0)
    private val bookTwo = book("book-2", "book-two", 1)
    private val cycle = Cycle(
        id = "cycle-1",
        slug = "cycle",
        title = "Cycle",
        bookOrder = listOf(bookOne.slug, bookTwo.slug),
        books = listOf(bookOne, bookTwo),
    )
    private val tracksByBookId = mapOf(
        bookOne.id to listOf(track("t1", bookOne.id, 0, 100_000)),
        bookTwo.id to listOf(
            track("t2", bookTwo.id, 0, 100_000),
            track("t3", bookTwo.id, 1, 100_000),
        ),
    )

    @Test
    fun `cycle fraction combines progress across books`() {
        val fraction = resolveCycleListenFraction(
            cycle = cycle,
            tracksByBookId = tracksByBookId,
            progressByBookId = mapOf(
                bookOne.id to progress(bookOne.id, "t1", 100_000),
                bookTwo.id to progress(bookTwo.id, "t2", 50_000),
            ),
        )
        assertEquals(0.5f, fraction)
    }

    @Test
    fun `resume returns first track when nothing listened`() {
        val resume = resolveCycleResumeTarget(cycle, tracksByBookId, emptyMap())
        assertEquals("t1", resume?.track?.id)
        assertEquals(0L, resume?.startPositionMs)
    }

    @Test
    fun `resume continues partial chapter`() {
        val resume = resolveCycleResumeTarget(
            cycle,
            tracksByBookId,
            mapOf(
                bookOne.id to progress(bookOne.id, "t1", 100_000),
                bookTwo.id to progress(bookTwo.id, "t2", 40_000),
            ),
        )
        assertEquals("t2", resume?.track?.id)
        assertEquals(40_000L, resume?.startPositionMs)
    }

    @Test
    fun `resume starts next chapter after completed one`() {
        val resume = resolveCycleResumeTarget(
            cycle,
            tracksByBookId,
            mapOf(
                bookOne.id to progress(bookOne.id, "t1", 100_000),
                bookTwo.id to progress(bookTwo.id, "t2", 100_000),
            ),
        )
        assertEquals("t3", resume?.track?.id)
        assertEquals(0L, resume?.startPositionMs)
    }

    @Test
    fun `cycle fraction is null without durations`() {
        val fraction = resolveCycleListenFraction(
            cycle = cycle,
            tracksByBookId = mapOf(bookOne.id to listOf(track("t1", bookOne.id, 0, null))),
            progressByBookId = emptyMap(),
        )
        assertNull(fraction)
    }

    @Test
    fun `continue state returns latest resumable chapter in cycle`() {
        val continueState = resolveCycleContinueState(
            cycle = cycle,
            tracksByBookId = tracksByBookId,
            progressByBookId = mapOf(
                bookOne.id to progress(bookOne.id, "t1", 95_000),
                bookTwo.id to progress(bookTwo.id, "t2", 12_000, updatedAtEpochMs = 2L),
            ),
        )
        assertEquals(BookContinueState("t2", 12_000), continueState)
    }

    @Test
    fun `continue state is null for another cycle without its own progress`() {
        val otherCycle = Cycle(
            id = "cycle-2",
            slug = "cycle-2",
            title = "Other",
            bookOrder = listOf("book-three"),
            books = listOf(
                book("book-3", "book-three", 0),
            ),
        )
        val otherTracks = mapOf(
            "book-3" to listOf(track("t3", "book-3", 0, 100_000)),
        )
        assertNull(
            resolveCycleContinueState(
                otherCycle,
                otherTracks,
                mapOf(bookTwo.id to progress(bookTwo.id, "t2", 12_000)),
            ),
        )
    }

    private fun book(id: String, slug: String, sortOrder: Int) = Book(
        id = id,
        slug = slug,
        contentType = ContentType.AUDIOBOOK,
        title = slug,
        author = "Author",
    )

    private fun track(id: String, bookId: String, sortOrder: Int, durationMs: Long?) = Track(
        id = id,
        bookId = bookId,
        sortOrder = sortOrder,
        title = id,
        filename = "$id.mp3",
        durationMs = durationMs,
        localPath = null,
    )

    private fun progress(bookId: String, trackId: String, positionMs: Long, updatedAtEpochMs: Long = 1L) =
        AudiobookProgress(
            bookId = bookId,
            trackId = trackId,
            positionMs = positionMs,
            updatedAtEpochMs = updatedAtEpochMs,
        )
}
