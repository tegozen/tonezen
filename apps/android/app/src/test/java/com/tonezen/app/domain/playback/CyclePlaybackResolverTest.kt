package com.tonezen.app.domain.playback

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CyclePlaybackResolverTest {
    private val resolver = CyclePlaybackResolver()

    private val book1 = Book("b1", "book-one", ContentType.AUDIOBOOK, "Book One", "Author")
    private val book2 = Book("b2", "book-two", ContentType.AUDIOBOOK, "Book Two", "Author")
    private val cycle = Cycle("c1", "cycle", "Cycle", listOf("book-one", "book-two"), listOf(book1, book2))

    private val t1 = Track("t1", "b1", 0, "Intro", "001.mp3", 1000, null)
    private val t2 = Track("t2", "b1", 1, "Chapter", "002.mp3", 2000, null)
    private val t3 = Track("t3", "b2", 0, "Start", "001.mp3", 1500, null)

    @Test
    fun advancesToNextTrackInBook() {
        val next = resolver.nextInBook(t1, listOf(t1, t2))
        assertEquals(t2, next)
    }

    @Test
    fun advancesToPreviousTrackInBook() {
        val previous = resolver.previousInBook(t2, listOf(t1, t2))
        assertEquals(t1, previous)
        assertNull(resolver.previousInBook(t1, listOf(t1, t2)))
    }

    @Test
    fun advancesToNextBookInCycle() {
        val result = resolver.nextInCycle(
            currentBook = book1,
            currentTrack = t2,
            cycle = cycle,
            booksBySlug = mapOf("book-one" to book1, "book-two" to book2),
            tracksByBookId = mapOf("b1" to listOf(t1, t2), "b2" to listOf(t3)),
        )
        assertTrue(result.isNextBookInCycle)
        assertEquals(t3, result.track)
        assertEquals(book2, result.book)
    }

    @Test
    fun returnsNullAtEndOfCycle() {
        val result = resolver.nextInCycle(
            currentBook = book2,
            currentTrack = t3,
            cycle = cycle,
            booksBySlug = mapOf("book-one" to book1, "book-two" to book2),
            tracksByBookId = mapOf("b1" to listOf(t1, t2), "b2" to listOf(t3)),
        )
        assertNull(result.track)
    }
}
