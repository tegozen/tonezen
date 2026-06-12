package com.tonezen.app.playback

import com.tonezen.app.domain.music.MusicLibraryTrack
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MusicPlaybackQueue @Inject constructor() {
    private var tracks: List<MusicLibraryTrack> = emptyList()

    fun set(tracks: List<MusicLibraryTrack>) {
        this.tracks = tracks
    }

    fun get(): List<MusicLibraryTrack> = tracks

    fun clear() {
        tracks = emptyList()
    }

    fun isActive(): Boolean = tracks.isNotEmpty()
}
