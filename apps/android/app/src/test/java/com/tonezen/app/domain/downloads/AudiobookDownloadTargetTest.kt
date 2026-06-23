package com.tonezen.app.domain.downloads

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudiobookDownloadTargetTest {
    private val book = Book(
        id = "book-1",
        slug = "book-1",
        title = "Book",
        contentType = ContentType.AUDIOBOOK,
        author = null,
    )

    private val tracks = listOf(
        track("track-1", 0, localPath = "local/001.mp3"),
        track("track-2", 1),
        track("track-3", 2),
    )

    @Test
    fun queuesOnlyNextMissingTrackAfterCurrent() {
        val request = nextAudiobookDownloadRequest(
            book = book,
            tracks = tracks,
            currentTrackId = "track-1",
            savedTrackId = null,
        )

        assertEquals("book-1", request?.bookId)
        assertEquals("track-2", request?.trackId)
        assertEquals(DownloadPriority.USER, request?.priority)
        assertEquals("002", request?.title)
        assertEquals("Book", request?.subtitle)
        assertEquals("audiobook", request?.contentType)
    }

    @Test
    fun doesNotWrapToEarlierMissingTracksWhileCurrentTrackIsKnown() {
        val request = nextAudiobookDownloadRequest(
            book = book,
            tracks = listOf(
                track("track-1", 0),
                track("track-2", 1, localPath = "local/002.mp3"),
                track("track-3", 2, localPath = "local/003.mp3"),
            ),
            currentTrackId = "track-2",
            savedTrackId = null,
        )

        assertNull(request)
    }

    private fun track(id: String, sortOrder: Int, localPath: String? = null) = Track(
        id = id,
        bookId = "book-1",
        sortOrder = sortOrder,
        title = "%03d".format(sortOrder + 1),
        filename = "$id.mp3",
        durationMs = 120_000L,
        localPath = localPath,
    )
}
