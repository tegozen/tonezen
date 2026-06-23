package com.tonezen.app.domain.progress

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track

fun completedAudiobookProgress(
    bookId: String,
    contentType: ContentType,
    track: Track?,
    fallbackDurationMs: Long,
    updatedAtEpochMs: Long = System.currentTimeMillis(),
): AudiobookProgress? {
    if (contentType != ContentType.AUDIOBOOK || track == null || track.bookId != bookId) return null
    val positionMs = maxOf(track.durationMs ?: 0L, fallbackDurationMs, 0L)
    if (positionMs <= 0L) return null
    return AudiobookProgress(
        bookId = bookId,
        trackId = track.id,
        positionMs = positionMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}
