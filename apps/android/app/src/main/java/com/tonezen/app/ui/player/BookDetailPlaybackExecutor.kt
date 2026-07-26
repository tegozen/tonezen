package com.tonezen.app.ui.player

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.EnqueueDownloadRequest
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicQueueWindow
import com.tonezen.app.domain.music.MusicShuffleQueue
import com.tonezen.app.playback.MusicPlaybackQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackQueueBuilder
import com.tonezen.app.playback.TrackDownloadQueueController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Строит очередь воспроизведения и запускает плеер для конкретной главы/трека книги. */
internal class BookDetailPlaybackExecutor(
    private val uiState: MutableStateFlow<BookDetailUiState>,
    private val catalogRepository: CatalogRepository,
    private val playbackClient: PlaybackClient,
    private val playbackQueueBuilder: PlaybackQueueBuilder,
    private val trackDownloadEnsurer: TrackDownloadEnsurer,
    private val networkMonitor: NetworkMonitor,
    private val downloadQueueController: TrackDownloadQueueController,
    private val localLibraryNotifier: LocalLibraryNotifier,
    private val musicPlaybackQueue: MusicPlaybackQueue,
    private val loadBook: (Book) -> Unit,
    private val onTrackStarted: (Track) -> Unit,
) {
    suspend fun playAudiobookTrack(
        book: Book,
        tracks: List<Track>,
        targetTrack: Track,
        startMs: Long,
    ) {
        uiState.update { it.copy(playbackErrorMessage = null) }
        val localTrack = if (!targetTrack.localPath.isNullOrBlank()) {
            targetTrack
        } else {
            val awaitResult = downloadQueueController.awaitTrack(
                bookId = book.id,
                trackId = targetTrack.id,
                priority = DownloadPriority.PLAY,
                title = targetTrack.title,
                subtitle = book.title,
                contentType = book.contentType.name.lowercase(),
            )
            if (awaitResult != DownloadAwaitResult.COMPLETED) {
                uiState.update {
                    it.copy(playbackErrorMessage = playbackErrorMessage(awaitResult))
                }
                return
            }
            withContext(Dispatchers.IO) {
                trackDownloadEnsurer.resolveLocalTrack(book.id, targetTrack)
            } ?: run {
                uiState.update {
                    it.copy(playbackErrorMessage = playbackErrorMessage(DownloadAwaitResult.FAILED))
                }
                return
            }.also {
                localLibraryNotifier.notifyLocalLibraryChanged()
            }
        }
        val refreshedTracks = withContext(Dispatchers.IO) {
            catalogRepository.getTracksForBook(book.id).sortedBy { it.sortOrder }
        }
        val tracksForQueue = refreshedTracks.map { t ->
            if (t.id == localTrack.id && t.localPath.isNullOrBlank()) localTrack else t
        }
        val queue = playbackQueueBuilder.buildQueueFromLocalTracks(book, tracksForQueue)
        if (queue.isEmpty()) return
        val startIndex = queue.indexOfFirst { it.trackId == localTrack.id }
        if (startIndex < 0) return
        onTrackStarted(localTrack)
        playbackClient.playQueue(queue, startIndex, startMs)
        prefetchNextChapter(book, tracks, localTrack)
        uiState.update { it.copy(activeTrackId = localTrack.id) }
        loadBook(book)
    }

    private fun prefetchNextChapter(book: Book, tracks: List<Track>, currentTrack: Track) {
        if (!networkMonitor.isOnline()) return
        val sorted = tracks.sortedBy { it.sortOrder }
        val index = sorted.indexOfFirst { it.id == currentTrack.id }
        if (index < 0 || index >= sorted.lastIndex) return
        val next = sorted[index + 1]
        if (!next.localPath.isNullOrBlank()) return
        downloadQueueController.enqueue(
            EnqueueDownloadRequest(
                bookId = book.id,
                trackId = next.id,
                priority = DownloadPriority.PREFETCH,
                title = next.title,
                subtitle = book.title,
                contentType = book.contentType.name.lowercase(),
            ),
        )
    }

    suspend fun playMusicTrack(book: Book, track: Track) {
        val libraryTracks = withContext(Dispatchers.IO) {
            val catalog = catalogRepository.resolveMusicLibraryTracks()
            MusicShuffleQueue.order(catalog, track.id)
        }
        musicPlaybackQueue.set(libraryTracks)
        val target = libraryTracks.find { it.track.id == track.id } ?: return
        val localTrack = withContext(Dispatchers.IO) {
            trackDownloadEnsurer.ensureTrackLocal(target.book.id, track).track
        } ?: run {
            uiState.update {
                it.copy(playbackErrorMessage = "Не удалось скачать трек")
            }
            return
        }
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
        if (queue.isEmpty()) {
            uiState.update {
                it.copy(playbackErrorMessage = "Не удалось скачать трек")
            }
            return
        }
        val startIndex = queue.indexOfFirst { it.trackId == localTrack.id }.coerceAtLeast(0)
        onTrackStarted(localTrack)
        playbackClient.playQueue(queue, startIndex)
        uiState.update { it.copy(activeTrackId = track.id) }
        loadBook(book)
    }

    private fun playbackErrorMessage(failure: EnsureTrackOutcome.Failure?): String = when (failure) {
        EnsureTrackOutcome.Failure.OFFLINE -> "Нет сети — нужен интернет для первой загрузки"
        EnsureTrackOutcome.Failure.NO_SESSION -> "Войдите в аккаунт, чтобы скачать трек"
        EnsureTrackOutcome.Failure.DOWNLOAD_FAILED, null -> "Не удалось скачать трек"
    }

    private fun playbackErrorMessage(result: DownloadAwaitResult): String = when (result) {
        DownloadAwaitResult.OFFLINE -> "Нет сети — нужен интернет для первой загрузки"
        DownloadAwaitResult.FAILED -> "Не удалось скачать трек"
        DownloadAwaitResult.CANCELLED, DownloadAwaitResult.COMPLETED -> "Не удалось скачать трек"
    }
}
