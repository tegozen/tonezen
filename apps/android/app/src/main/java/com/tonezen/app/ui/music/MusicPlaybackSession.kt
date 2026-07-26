package com.tonezen.app.ui.music

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicLibraryTrack

internal class MusicPlaybackSession {
    var musicBookIdByTrackId: Map<String, String> = emptyMap()
    var musicCandidates: List<Pair<Book, Track>> = emptyList()
    var musicStartedInSession: Boolean = false
    var musicLibraryTracks: List<MusicLibraryTrack> = emptyList()
    var lastPrefetchSourceTrackId: String? = null
}
