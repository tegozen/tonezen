package com.tonezen.app.ui.music

import com.tonezen.app.playback.PlaybackSnapshot

internal data class MusicSnapshotUiKey(
    val trackId: String?,
    val isPlaying: Boolean,
    val isMusic: Boolean,
    val trackTitle: String?,
    val artist: String?,
    val albumTitle: String?,
) {
    companion object {
        fun from(snapshot: PlaybackSnapshot, isMusic: Boolean) = MusicSnapshotUiKey(
            trackId = snapshot.trackId,
            isPlaying = snapshot.isPlaying,
            isMusic = isMusic,
            trackTitle = snapshot.trackTitle,
            artist = snapshot.artist,
            albumTitle = snapshot.albumTitle,
        )
    }
}
