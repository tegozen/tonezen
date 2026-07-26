package com.tonezen.app.domain.model

enum class ContentType { AUDIOBOOK, MUSIC }

enum class SessionState {
    AUTHENTICATED_ONLINE,
    AUTHENTICATED_OFFLINE,
    AUTHENTICATED_STALE,
    UNAUTHENTICATED,
}

data class StoredSession(
    val userId: String,
    val email: String,
    val displayName: String,
    val accessToken: String,
    val refreshToken: String,
    val expiresAtEpochSeconds: Long,
    val memberSinceEpochMs: Long? = null,
    val avatarUrl: String? = null,
)

data class AudiobookProgress(
    val bookId: String,
    val trackId: String,
    val positionMs: Long,
    val updatedAtEpochMs: Long,
    val revision: Long = 0L,
    val serverTrackId: String? = null,
    val serverPositionMs: Long? = null,
    val serverRevision: Long? = null,
    val conflictChoiceKey: String? = null,
)

data class Book(
    val id: String,
    val slug: String,
    val contentType: ContentType,
    val title: String,
    val author: String?,
)

data class Track(
    val id: String,
    val bookId: String,
    val sortOrder: Int,
    val title: String,
    val filename: String,
    val artist: String? = null,
    val durationMs: Long?,
    val localPath: String?,
    val localDownloadedAt: Long? = null,
    val waveformPeaks: List<Int>? = null,
)

data class Cycle(
    val id: String,
    val slug: String,
    val title: String,
    val bookOrder: List<String>,
    val books: List<Book>,
)

data class PlaybackQueueItem(
    val track: Track,
    val book: Book,
)
