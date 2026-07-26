package com.tonezen.app.data.local

import android.content.Context
import com.tonezen.app.data.remote.progress.ProgressRemoteApi
import com.tonezen.app.data.waveformPeaksFromJson
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.normalizeAuthor
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.Track
import java.time.Instant
import org.json.JSONArray

fun BookEntity.toDomain() = Book(
    id = id,
    slug = slug,
    contentType = if (contentType == "music") ContentType.MUSIC else ContentType.AUDIOBOOK,
    title = title,
    author = normalizeAuthor(author),
)

fun TrackEntity.toDomain() = Track(
    id = id,
    bookId = bookId,
    sortOrder = sortOrder,
    title = title,
    filename = filename,
    artist = normalizeAuthor(artist),
    durationMs = durationMs,
    localPath = localPath,
    localDownloadedAt = localDownloadedAt,
    waveformPeaks = waveformPeaksFromJson(waveformPeaksJson),
)

fun TrackEntity.toSanitizedDomain(context: Context): Track {
    val safePath = SafeLocalStorage.sanitizeStoredLocalPath(context.filesDir, localPath)
    return Track(
        id = id,
        bookId = bookId,
        sortOrder = sortOrder,
        title = title,
        filename = filename,
        artist = normalizeAuthor(artist),
        durationMs = durationMs,
        localPath = safePath,
        localDownloadedAt = localDownloadedAt,
        waveformPeaks = waveformPeaksFromJson(waveformPeaksJson),
    )
}

fun AudiobookProgressEntity.toDomain() = AudiobookProgress(
    bookId = bookId,
    trackId = trackId,
    positionMs = positionMs,
    updatedAtEpochMs = updatedAtEpochMs,
    revision = revision,
    serverTrackId = serverTrackId,
    serverPositionMs = serverPositionMs,
    serverRevision = serverRevision,
    conflictChoiceKey = conflictChoiceKey,
)

fun AudiobookProgress.toEntity(userId: String, pendingSync: Boolean = false) = AudiobookProgressEntity(
    userId = userId,
    bookId = bookId,
    trackId = trackId,
    positionMs = positionMs,
    updatedAtEpochMs = updatedAtEpochMs,
    pendingSync = pendingSync,
    revision = revision,
    serverTrackId = serverTrackId,
    serverPositionMs = serverPositionMs,
    serverRevision = serverRevision,
    conflictChoiceKey = conflictChoiceKey,
)

fun ProgressRemoteApi.RemoteProgress.toProgressEntity(userId: String) = AudiobookProgressEntity(
    userId = userId,
    bookId = bookId,
    trackId = trackId,
    positionMs = positionMs,
    updatedAtEpochMs = Instant.parse(updatedAt).toEpochMilli(),
    pendingSync = false,
    revision = revision,
    serverTrackId = trackId,
    serverPositionMs = positionMs,
    serverRevision = revision,
)

fun CycleEntity.toDomain(booksById: Map<String, Book>): Cycle? {
    val bookIds = JSONArray(bookOrderJson).let { array ->
        buildList {
            for (index in 0 until array.length()) {
                add(array.getString(index))
            }
        }
    }
    val books = bookIds.mapNotNull { booksById[it] }
    if (books.isEmpty()) return null
    return Cycle(
        id = id,
        slug = slug,
        title = title,
        bookOrder = books.map { it.slug },
        books = books,
    )
}
