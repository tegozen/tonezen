package com.tonezen.app.domain.music

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Test

class MusicLibraryResolverTest {
    private val libraryBook = Book("lib-1", "music-library", ContentType.MUSIC, "Music", null)

    @Test
    fun `prefers music-library book over legacy split entries`() {
        val libraryBook = Book("lib-1", "music-library", ContentType.MUSIC, "Music", null)
        val legacyBook = Book("old-1", "track-a", ContentType.MUSIC, "Track A", "Artist")
        val tracks = mapOf(
            "lib-1" to listOf(
                track("t1", "lib-1", 0, "One", "01-one.mp3"),
                track("t2", "lib-1", 1, "Two", "02-two.mp3"),
            ),
            "old-1" to listOf(track("legacy", "old-1", 0, "Legacy", "legacy.mp3")),
        )
        val result = MusicLibraryResolver.resolve(
            allBooks = listOf(libraryBook, legacyBook),
            tracksForBook = { bookId -> tracks[bookId].orEmpty() },
        )
        assertEquals(listOf("t1", "t2"), result.map { it.track.id })
    }

    @Test
    fun `returns all music tracks from every music book when library slug missing`() {
        val legacyBook = Book("old-1", "track-a", ContentType.MUSIC, "Track A", "Artist")
        val tracks = mapOf(
            "old-1" to listOf(track("t3", "old-1", 0, "Legacy", "legacy.mp3")),
        )
        val result = MusicLibraryResolver.resolve(
            allBooks = listOf(legacyBook),
            tracksForBook = { bookId -> tracks[bookId].orEmpty() },
        )
        assertEquals(listOf("t3"), result.map { it.track.id })
    }

    @Test
    fun `sorts by sort order then filename`() {
        val result = MusicLibraryResolver.resolve(
            allBooks = listOf(libraryBook),
            tracksForBook = {
                listOf(
                    track("t2", "lib-1", 1, "B", "02-b.mp3"),
                    track("t1", "lib-1", 0, "A", "01-a.mp3"),
                )
            },
        )
        assertEquals(listOf("t1", "t2"), result.map { it.track.id })
    }

    private fun track(id: String, bookId: String, sortOrder: Int, title: String, filename: String) = Track(
        id = id,
        bookId = bookId,
        sortOrder = sortOrder,
        title = title,
        filename = filename,
        durationMs = 60_000L,
        localPath = null,
    )
}
