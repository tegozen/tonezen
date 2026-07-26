package com.tonezen.app.ui.player

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.EnqueueDownloadRequest
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.Track
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.TrackDownloadQueueController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Действия по загрузке/удалению глав и книги на экране книги. */
internal class BookDetailDownloadActions(
    private val uiState: MutableStateFlow<BookDetailUiState>,
    private val trackDownloads: MutableStateFlow<Map<String, BookDetailTrackDownloadUi>>,
    private val playbackProgress: MutableStateFlow<BookDetailPlaybackProgress>,
    private val scope: CoroutineScope,
    private val catalogRepository: CatalogRepository,
    private val downloadRepository: DownloadRepository,
    private val networkMonitor: NetworkMonitor,
    private val downloadQueueController: TrackDownloadQueueController,
    private val downloadQueueNotifier: DownloadQueueNotifier,
    private val localLibraryNotifier: LocalLibraryNotifier,
    private val playbackClient: PlaybackClient,
    private val loadBook: (Book) -> Unit,
) {
    /** Подписывается на состояние очереди загрузок, обновляя per-track scalars. */
    fun startObserving() {
        scope.launch {
            downloadQueueNotifier.state.collect { queueState ->
                val bookId = uiState.value.book?.id
                val trackIds = uiState.value.tracks.map { it.id }.toSet()
                if (bookId == null || trackIds.isEmpty()) {
                    trackDownloads.value = emptyMap()
                    return@collect
                }
                val next = trackIds.associateWith { trackId ->
                    BookDetailTrackDownloadUi(
                        progress = queueState.progressForTrack(trackId),
                        isQueued = queueState.isTrackQueued(trackId),
                    )
                }
                if (next != trackDownloads.value) {
                    trackDownloads.value = next
                }
            }
        }
    }

    fun requestDownload() {
        downloadAllMissingTracks()
    }

    fun requestTrackDownload(track: Track) {
        val book = uiState.value.book ?: return
        if (!track.localPath.isNullOrBlank()) return
        if (!networkMonitor.isOnline()) {
            uiState.update { it.copy(error = BookDetailViewModel.DOWNLOAD_OFFLINE_ERROR) }
            return
        }
        downloadQueueController.enqueue(
            EnqueueDownloadRequest(
                bookId = book.id,
                trackId = track.id,
                priority = DownloadPriority.USER,
                title = track.title,
                subtitle = book.title,
                contentType = book.contentType.name.lowercase(),
            ),
        )
    }

    fun clearDownloadError() {
        uiState.update { it.copy(error = null) }
    }

    private fun downloadAllMissingTracks() {
        val book = uiState.value.book ?: return
        val missingTracks = uiState.value.tracks
            .sortedBy { it.sortOrder }
            .filter { it.localPath.isNullOrBlank() }
        if (missingTracks.isEmpty()) return
        val batchId = java.util.UUID.randomUUID().toString()
        val requests = missingTracks.map { track ->
            EnqueueDownloadRequest(
                bookId = book.id,
                trackId = track.id,
                priority = DownloadPriority.USER,
                batchId = batchId,
                title = track.title,
                subtitle = book.title,
                contentType = book.contentType.name.lowercase(),
            )
        }
        downloadQueueController.enqueueBatch(requests, batchId)
    }

    fun deleteLocalDownloads() {
        val book = uiState.value.book ?: return
        scope.launch {
            val snapshot = playbackClient.snapshot.value
            val isCurrentBook = snapshot.trackId != null &&
                uiState.value.tracks.any { it.id == snapshot.trackId }
            if (isCurrentBook) {
                playbackClient.stopAndRelease()
                playbackProgress.value = BookDetailPlaybackProgress()
                uiState.update { it.copy(activeTrackId = null) }
            }
            catalogRepository.clearLocalDownloads(book.id)
            val tracks = catalogRepository.getTracksForBook(book.id)
            tracks.forEach { track ->
                downloadRepository.deleteLocalTrack(book.id, track.id)
            }
            localLibraryNotifier.notifyLocalLibraryChanged()
            loadBook(book)
        }
    }

    fun removeTrackDownload(track: Track) {
        val book = uiState.value.book ?: return
        scope.launch {
            downloadRepository.deleteLocalTrack(book.id, track.id)
            catalogRepository.clearTrackLocalPath(book.id, track.id)
            localLibraryNotifier.notifyLocalLibraryChanged()
            loadBook(book)
        }
    }
}
