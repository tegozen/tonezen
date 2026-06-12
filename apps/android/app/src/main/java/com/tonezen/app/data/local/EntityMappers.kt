package com.tonezen.app.data.local

import com.tonezen.app.data.remote.ApiClient
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import java.time.Instant

fun BookEntity.toDomain() = Book(
    id = id,
    slug = slug,
    contentType = if (contentType == "music") ContentType.MUSIC else ContentType.AUDIOBOOK,
    title = title,
    author = author,
)

fun TrackEntity.toDomain() = Track(
    id = id,
    bookId = bookId,
    sortOrder = sortOrder,
    title = title,
    filename = filename,
    durationMs = durationMs,
    localPath = localPath,
)

fun AudiobookProgressEntity.toDomain() = AudiobookProgress(
    bookId = bookId,
    trackId = trackId,
    positionMs = positionMs,
    updatedAtEpochMs = updatedAtEpochMs,
)

fun AudiobookProgress.toEntity(pendingSync: Boolean = false) = AudiobookProgressEntity(
    bookId = bookId,
    trackId = trackId,
    positionMs = positionMs,
    updatedAtEpochMs = updatedAtEpochMs,
    pendingSync = pendingSync,
)

fun ApiClient.RemoteProgress.toProgressEntity() = AudiobookProgressEntity(
    bookId = bookId,
    trackId = trackId,
    positionMs = positionMs,
    updatedAtEpochMs = Instant.parse(updatedAt).toEpochMilli(),
    pendingSync = false,
)
