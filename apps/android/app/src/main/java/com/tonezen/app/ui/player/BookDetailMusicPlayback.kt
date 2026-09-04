package com.tonezen.app.ui.player

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicQueueWindow
import com.tonezen.app.domain.music.MusicShuffleQueue
import com.tonezen.app.playback.MusicPlaybackQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackQueueBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

internal class BookDetailMusicPlayback(
    private val uiState: MutableStateFlow<BookDetailUiState>,
    private val catalogRepository: CatalogRepository,
    private val playbackClient: PlaybackClient,
    private val playbackQueueBuilder: PlaybackQueueBuilder,
    private val trackDownloadEnsurer: TrackDownloadEnsurer,
    private val musicPlaybackQueue: MusicPlaybackQueue,
    private val loadBook: (Book) -> Unit,
) {
    suspend fun play(book: Book, track: Track) {
        val libraryTracks = withContext(Dispatchers.IO) {
            MusicShuffleQueue.order(catalogRepository.resolveMusicLibraryTracks(), track.id)
        }
        musicPlaybackQueue.set(libraryTracks)
        val target = libraryTracks.find { it.track.id == track.id } ?: return
        val localTrack = withContext(Dispatchers.IO) {
            trackDownloadEnsurer.ensureTrackLocal(target.book.id, track).track
        } ?: return showDownloadError()
        val queueWindow = MusicQueueWindow.initialWindow(
            items = libraryTracks,
            startTrackId = localTrack.id,
            idOf = { it.track.id },
        )
        val queue = withContext(Dispatchers.IO) {
            playbackQueueBuilder.buildLocalMusicLibraryQueue(queueWindow) { entry ->
                if (entry.track.id == localTrack.id) {
                    localTrack
                } else {
                    trackDownloadEnsurer.resolveLocalTrack(entry.book.id, entry.track)
                }
            }
        }
        if (queue.isEmpty()) return showDownloadError()
        val startIndex = queue.indexOfFirst { it.trackId == localTrack.id }.coerceAtLeast(0)
        playbackClient.playQueue(queue, startIndex)
        uiState.update { it.copy(activeTrackId = track.id) }
        loadBook(book)
    }

    private fun showDownloadError() {
        uiState.update { it.copy(playbackErrorMessage = "Не удалось скачать трек") }
    }
}
