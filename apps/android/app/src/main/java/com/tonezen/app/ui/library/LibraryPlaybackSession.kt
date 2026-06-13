package com.tonezen.app.ui.library

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicLibraryTrack

internal class LibraryPlaybackSession {
    var musicBookIdByTrackId: Map<String, String> = emptyMap()
    var musicCandidates: List<Pair<Book, Track>> = emptyList()
    var musicStartedInSession: Boolean = false
    var musicLibraryTracks: List<MusicLibraryTrack> = emptyList()
    var lastPrefetchSourceTrackId: String? = null
    var activeAudiobookBookId: String? = null
    var activeAudiobookTrackId: String? = null
    var lastAudiobookProgressSaveMs: Long = 0L
    var wasAudiobookPlaying: Boolean = false
}
