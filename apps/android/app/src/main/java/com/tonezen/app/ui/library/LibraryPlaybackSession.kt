package com.tonezen.app.ui.library

internal class LibraryPlaybackSession {
    var activeAudiobookBookId: String? = null
    var activeAudiobookTrackId: String? = null
    var lastAudiobookProgressSaveMs: Long = 0L
    var wasAudiobookPlaying: Boolean = false
}
