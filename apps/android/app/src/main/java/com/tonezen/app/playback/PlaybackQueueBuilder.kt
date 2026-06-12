package com.tonezen.app.playback

import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicLibraryTrack
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaybackQueueBuilder @Inject constructor(
    private val trackDownloadEnsurer: TrackDownloadEnsurer,
) {
    fun buildQueueFromLocalTracks(book: Book, tracks: List<Track>): List<QueuePlayItem> {
        val localTracks = tracks.filter { !it.localPath.isNullOrBlank() }
        return buildQueue(book, localTracks)
    }

    suspend fun buildMusicQueue(book: Book, tracks: List<Track>): List<QueuePlayItem> {
        if (book.contentType != ContentType.MUSIC) return emptyList()
        return buildQueue(book, trackDownloadEnsurer.ensureTracksLocal(book, tracks))
    }

    suspend fun buildSingleMusicItem(
        book: Book,
        track: Track,
        onProgress: ((Float) -> Unit)? = null,
    ): QueuePlayItem? {
        if (book.contentType != ContentType.MUSIC) return null
        val outcome = trackDownloadEnsurer.ensureTrackLocal(book.id, track, onProgress)
        val local = outcome.track ?: return null
        return singleQueueItem(book, local)
    }

    fun itemForLocalTrack(book: Book, track: Track): QueuePlayItem = singleQueueItem(book, track)

    fun itemForMusicLibraryTrack(
        entry: MusicLibraryTrack,
        localTrack: Track,
        indexInLibrary: Int,
        librarySize: Int,
    ): QueuePlayItem = queueItem(entry.book, localTrack, indexInLibrary + 1, librarySize)

    suspend fun buildLocalMusicLibraryQueue(
        libraryTracks: List<MusicLibraryTrack>,
        resolveLocalTrack: suspend (MusicLibraryTrack) -> Track?,
    ): List<QueuePlayItem> {
        val localEntries = libraryTracks.mapNotNull { entry ->
            resolveLocalTrack(entry)?.let { local -> entry.book to local }
        }
        if (localEntries.isEmpty()) return emptyList()
        return localEntries.mapIndexed { index, (book, track) ->
            queueItem(book, track, index + 1, libraryTracks.size)
        }
    }

    suspend fun buildSingleAudiobookItem(book: Book, track: Track): QueuePlayItem? {
        if (book.contentType != ContentType.AUDIOBOOK) return null
        val outcome = trackDownloadEnsurer.ensureTrackLocal(book.id, track)
        val local = outcome.track ?: return null
        return singleQueueItem(book, local)
    }

    suspend fun buildAudiobookQueue(book: Book, tracks: List<Track>): List<QueuePlayItem> {
        if (book.contentType != ContentType.AUDIOBOOK) return emptyList()
        return buildQueue(book, trackDownloadEnsurer.ensureTracksLocal(book, tracks))
    }

    private fun buildQueue(book: Book, localTracks: List<Track>): List<QueuePlayItem> {
        if (localTracks.isEmpty()) return emptyList()
        return localTracks.mapIndexed { index, track ->
            queueItem(book, track, index + 1, localTracks.size)
        }
    }

    private fun singleQueueItem(book: Book, track: Track): QueuePlayItem =
        queueItem(book, track, track.sortOrder + 1, 1)

    private fun queueItem(book: Book, track: Track, trackNumber: Int, totalTracks: Int): QueuePlayItem {
        val path = track.localPath ?: error("Track ${track.id} missing local path after download")
        return QueuePlayItem(
            trackId = track.id,
            mediaUri = path,
            metadata = PlaybackMetadata(
                trackTitle = track.title,
                artist = book.author ?: book.title,
                albumTitle = book.title,
                trackNumber = trackNumber,
                totalTracks = totalTracks,
                contentType = book.contentType,
            ),
        )
    }
}
