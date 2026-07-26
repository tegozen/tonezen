package com.tonezen.app.ui.player

import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track

data class NowPlayingUiState(
    val title: String? = null,
    val subtitle: String? = null,
    val coverSeed: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val contentType: ContentType? = null,
    val activeBook: Book? = null,
    val upNext: List<Track> = emptyList(),
    val canSkipPrevious: Boolean = true,
    val canSkipNext: Boolean = false,
    val waveformPeaks: List<Int>? = null,
)

internal data class AlbumTrackEntry(
    val book: Book,
    val track: Track,
)

internal fun formatSubtitle(artist: String?, album: String?): String? {
    val cleanArtist = artist?.takeIf { it.isNotBlank() }
    val cleanAlbum = album?.takeIf { it.isNotBlank() }
    return when {
        cleanArtist != null && cleanAlbum != null -> "$cleanArtist · $cleanAlbum"
        cleanArtist != null -> cleanArtist
        cleanAlbum != null -> cleanAlbum
        else -> null
    }
}
