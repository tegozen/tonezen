package com.tonezen.app.domain.progress

import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompletedAudiobookProgressTest {
    private val track = Track(
        id = "track-10",
        bookId = "book-1",
        sortOrder = 9,
        title = "010",
        filename = "010.mp3",
        durationMs = 1_164_000L,
        localPath = null,
    )

    @Test
    fun buildsCompletedProgressForEndedChapter() {
        val progress = completedAudiobookProgress(
            bookId = "book-1",
            contentType = ContentType.AUDIOBOOK,
            track = track,
            fallbackDurationMs = 0L,
            updatedAtEpochMs = 1_710_000_000_000L,
        )

        assertEquals("book-1", progress?.bookId)
        assertEquals("track-10", progress?.trackId)
        assertEquals(1_164_000L, progress?.positionMs)
        assertEquals(1_710_000_000_000L, progress?.updatedAtEpochMs)
    }

    @Test
    fun ignoresNonAudiobookContent() {
        assertNull(
            completedAudiobookProgress(
                bookId = "book-1",
                contentType = ContentType.MUSIC,
                track = track,
                fallbackDurationMs = 1_164_000L,
            ),
        )
    }
}
