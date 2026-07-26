package com.tonezen.app.ui.player

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.music.MusicLibraryTrack
import com.tonezen.app.domain.music.MusicPlaybackAdvanceRules
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackQueueBuilder
import com.tonezen.app.playback.TrackDownloadQueueController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

/** Строит очередь воспроизведения и запускает трек по индексу в контексте альбома. */
internal class NowPlayingQueueExecutor(
    private val uiState: MutableStateFlow<NowPlayingUiState>,
    private val catalogContext: NowPlayingCatalogContext,
    private val catalogRepository: CatalogRepository,
    private val playbackClient: PlaybackClient,
    private val playbackQueueBuilder: PlaybackQueueBuilder,
    private val trackDownloadEnsurer: TrackDownloadEnsurer,
    private val downloadQueueController: TrackDownloadQueueController,
    private val networkMonitor: NetworkMonitor,
) {
    suspend fun playQueueAt(index: Int) {
        val entry = catalogContext.libraryTracks.getOrNull(index) ?: return
        val book = entry.book
        val target = entry.track
        val needsDownload = withContext(Dispatchers.IO) {
            target.localPath.isNullOrBlank() && !trackDownloadEnsurer.isTrackLocal(book.id, target.id)
        }

        if (needsDownload) {
            playbackClient.pause()
            val awaitResult = downloadQueueController.awaitTrack(
                bookId = book.id,
                trackId = target.id,
                priority = DownloadPriority.PLAY,
                title = target.title,
                subtitle = book.title,
                contentType = book.contentType.name.lowercase(),
            )
            if (awaitResult != DownloadAwaitResult.COMPLETED) {
                if (awaitResult == DownloadAwaitResult.FAILED && networkMonitor.isOnline()) {
                    return
                }
                val nextIndex = findNextPlayableIndex(index) ?: return
                playQueueAt(nextIndex)
                return
            }
        }

        val ensured = withContext(Dispatchers.IO) {
            catalogRepository.getTracksForBook(book.id)
                .find { it.id == target.id }
                ?.let { trackDownloadEnsurer.resolveLocalTrack(book.id, it) } != null
        }
        if (!ensured) {
            if (networkMonitor.isOnline()) {
                return
            }
            val nextIndex = findNextPlayableIndex(index) ?: return
            playQueueAt(nextIndex)
            return
        }

        uiState.update {
            it.copy(
                title = target.title,
                subtitle = formatSubtitle(book.author, book.title),
                coverSeed = target.id,
                waveformPeaks = target.waveformPeaks,
            )
        }

        val queue = buildLocalQueue()
        if (queue.isEmpty()) return
        val startIndex = queue.indexOfFirst { it.trackId == target.id }.takeIf { it >= 0 } ?: return
        playbackClient.playQueue(queue, startIndex)
        catalogContext.updateAlbumNavigation(index, book)
    }

    private fun findNextPlayableIndex(currentIndex: Int): Int? =
        MusicPlaybackAdvanceRules.findNextPlayable(
            items = catalogContext.libraryTracks,
            currentIndex = currentIndex,
            isPlayable = { entry -> catalogContext.isAlbumEntryPlayable(entry) },
        )

    private suspend fun buildLocalQueue() =
        playbackQueueBuilder.buildLocalMusicLibraryQueue(
            catalogContext.libraryTracks.map { MusicLibraryTrack(it.book, it.track) },
        ) { entry ->
            catalogRepository.getTracksForBook(entry.book.id)
                .find { it.id == entry.track.id }
                ?.takeIf { !it.localPath.isNullOrBlank() }
        }
}
