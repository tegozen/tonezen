package com.tonezen.app.ui.library

import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.playback.PlaybackSnapshot

internal data class LibrarySnapshotUiKey(
    val trackId: String?,
    val isPlaying: Boolean,
    val contentType: ContentType?,
    val trackTitle: String?,
    val artist: String?,
    val albumTitle: String?,
) {
    companion object {
        fun from(snapshot: PlaybackSnapshot) = LibrarySnapshotUiKey(
            trackId = snapshot.trackId,
            isPlaying = snapshot.isPlaying,
            contentType = snapshot.contentType,
            trackTitle = snapshot.trackTitle,
            artist = snapshot.artist,
            albumTitle = snapshot.albumTitle,
        )
    }
}

internal fun playbackErrorMessage(failure: EnsureTrackOutcome.Failure?): String = when (failure) {
    EnsureTrackOutcome.Failure.OFFLINE -> "Нет сети — нужен интернет для первой загрузки"
    EnsureTrackOutcome.Failure.NO_SESSION -> "Войдите в аккаунт, чтобы скачать трек"
    EnsureTrackOutcome.Failure.DOWNLOAD_FAILED, null -> "Не удалось скачать трек"
}
