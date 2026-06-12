package com.tonezen.app.domain.music

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicShuffleQueueTest {
    private val book = Book("lib-1", "music-library", ContentType.MUSIC, "Music", "Artist")

    @Test
    fun `start track is first and all tracks remain in order`() {
        val entries = tracks("a", "b", "c", "d")
        val ordered = MusicShuffleQueue.order(entries, "c")
        assertEquals("c", ordered.first().track.id)
        assertEquals(setOf("a", "b", "c", "d"), ordered.map { it.track.id }.toSet())
    }

    @Test
    fun `next and previous indices wrap around`() {
        assertEquals(0, MusicShuffleQueue.nextIndex(3, 4))
        assertEquals(3, MusicShuffleQueue.previousIndex(0, 4))
    }

    @Test
    fun `single track order is unchanged`() {
        val entries = tracks("only")
        assertEquals(entries, MusicShuffleQueue.order(entries, "only"))
    }

    @Test
    fun `shuffle keeps start first while permuting the rest`() {
        val entries = tracks("a", "b", "c", "d", "e", "f")
        val ordered = MusicShuffleQueue.order(entries, "d")
        assertEquals("d", ordered.first().track.id)
        assertTrue(ordered.drop(1).map { it.track.id }.distinct().size == 5)
    }

    private fun tracks(vararg ids: String) = ids.mapIndexed { index, id ->
        MusicLibraryTrack(
            book = book,
            track = Track(
                id = id,
                bookId = book.id,
                sortOrder = index,
                title = id,
                filename = "$id.mp3",
                durationMs = 60_000L,
                localPath = null,
            ),
        )
    }
}
