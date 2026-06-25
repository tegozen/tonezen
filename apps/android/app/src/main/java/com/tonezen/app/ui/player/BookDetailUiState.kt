package com.tonezen.app.ui.player

import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.playback.DownloadQueueState
import com.tonezen.app.playback.PlaybackSnapshot

enum class SyncDisplayStatus {
    NONE,
    SYNCED,
    PENDING,
}

data class BookDetailUiState(
    val book: Book? = null,
    val tracks: List<Track> = emptyList(),
    val activeTrackId: String? = null,
    val audiobookProgress: AudiobookProgress? = null,
    val playbackPositionMs: Long = 0L,
    val playbackDurationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isPlaybackActiveForBook: Boolean = false,
    val downloadProgress: Float? = null,
    val downloadQueueState: DownloadQueueState = DownloadQueueState(),
    val syncStatus: SyncDisplayStatus = SyncDisplayStatus.NONE,
    val error: String? = null,
    val playbackErrorMessage: String? = null,
)

data class BookDetailPlaybackState(
    val activeTrackId: String? = null,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isPlaying: Boolean = false,
    val isActiveForBook: Boolean = false,
)

fun resolveBookDetailPlaybackState(
    tracks: List<Track>,
    snapshot: PlaybackSnapshot,
): BookDetailPlaybackState {
    val trackId = snapshot.trackId?.takeIf { candidate ->
        tracks.any { it.id == candidate }
    } ?: return BookDetailPlaybackState()
    return BookDetailPlaybackState(
        activeTrackId = trackId,
        positionMs = snapshot.positionMs.coerceAtLeast(0L),
        durationMs = snapshot.durationMs.coerceAtLeast(0L),
        isPlaying = snapshot.isPlaying,
        isActiveForBook = true,
    )
}

fun bookDetailTracksForDisplay(tracks: List<Track>): List<Track> = tracks.sortedBy { it.sortOrder }
