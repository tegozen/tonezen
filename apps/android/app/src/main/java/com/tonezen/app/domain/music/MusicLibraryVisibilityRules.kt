package com.tonezen.app.domain.music

object MusicLibraryVisibilityRules {
    fun <T> visibleInLibrary(
        tracks: List<T>,
        isDownloaded: (T) -> Boolean,
        isNetworkOnline: Boolean,
    ): List<T> = if (isNetworkOnline) tracks else tracks.filter(isDownloaded)
}
