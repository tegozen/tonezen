package com.tonezen.app.ui.music

import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicDownloadInteractionRules
import com.tonezen.app.domain.music.MusicLibraryTrack
import com.tonezen.app.domain.music.MusicPlaybackAdvanceRules
import com.tonezen.app.domain.music.MusicQueueWindow
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.playback.QueuePlayItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update

internal class MusicPlayExecutor(
    private val ctx: MusicHandlerContext,
    private val catalogLists: MusicCatalogLists,
    private val prefetch: MusicPrefetch,
) {
    suspend fun playNextAvailableFrom(currentIndex: Int) {
        catalogLists.refreshMusicLibraryTracksLocalPaths()
        val library = ctx.session.musicLibraryTracks
        if (library.isEmpty()) return
        val nextIndex = MusicPlaybackAdvanceRules.findNextPlayable(
            items = library,
            currentIndex = currentIndex,
            isPlayable = { entry -> isMusicEntryPlayable(entry) },
        ) ?: run {
            ctx.playbackClient.pause()
            return
        }
        playMusicTrack(
            track = catalogLists.musicListTrackFromEntry(library[nextIndex]),
            showDownloadProgress = false,
            advancePlayback = true,
        )
    }

    suspend fun playMusicTrack(
        track: MusicListTrack,
        showDownloadProgress: Boolean,
        advancePlayback: Boolean = false,
    ) {
        val downloadState = ctx.musicDownloadInteractionState()
        if (MusicDownloadInteractionRules.blocksPlaybackAdvanceDuringBulk(downloadState)) {
            if (!track.isDownloaded || advancePlayback) return
        }
        if (!musicBookAvailable(track.bookId)) {
            ctx.reportMusicDownloadError()
            return
        }
        val libraryTracks = catalogLists.buildMusicLibraryTracksFromList()
        val resolvedTrack = withContext(Dispatchers.IO) {
            ctx.catalogRepository.findTrackInCatalog(track.trackId)
        } ?: run {
            ctx.reportMusicDownloadError()
            return
        }
        val bookId = resolvedTrack.bookId
        val needsDownload = withContext(Dispatchers.IO) {
            !ctx.trackDownloadEnsurer.isTrackLocal(bookId, resolvedTrack.id)
        }
        if (needsDownload) {
            val awaitResult = ctx.downloadQueueController.awaitTrack(
                bookId = bookId,
                trackId = resolvedTrack.id,
                priority = DownloadPriority.PLAY,
                title = track.trackTitle,
                subtitle = track.artist,
                contentType = ContentType.MUSIC.name.lowercase(),
            )
            if (awaitResult != DownloadAwaitResult.COMPLETED) {
                if (advancePlayback) {
                    val failedIndex = ctx.session.musicLibraryTracks.indexOfFirst { it.track.id == track.trackId }
                    if (failedIndex >= 0) playNextAvailableFrom(failedIndex)
                } else {
                    ctx.reportMusicDownloadError(awaitResult)
                }
                return
            }
            ctx.localLibraryNotifier.notifyLocalLibraryChanged()
        }
        val localTrack = withContext(Dispatchers.IO) {
            ctx.trackDownloadEnsurer.resolveLocalTrack(bookId, resolvedTrack)
        } ?: run {
            if (advancePlayback) {
                val failedIndex = ctx.session.musicLibraryTracks.indexOfFirst { it.track.id == track.trackId }
                if (failedIndex >= 0) playNextAvailableFrom(failedIndex)
            } else {
                ctx.reportMusicDownloadError()
            }
            return
        }
        val queueWindow = MusicQueueWindow.initialWindow(
            items = libraryTracks,
            startTrackId = localTrack.id,
            idOf = { it.track.id },
        )
        val queue = withContext(Dispatchers.IO) {
            buildLocalMusicQueueItems(
                libraryTracks = libraryTracks,
                windowEntries = queueWindow,
                forcedLocalTrack = localTrack,
            )
        }
        if (queue.isEmpty()) {
            ctx.uiState.update {
                it.copy(musicPlaybackErrorMessage = ctx.playbackErrorMessage(EnsureTrackOutcome.Failure.DOWNLOAD_FAILED))
            }
            return
        }
        ctx.session.musicStartedInSession = true
        val startIndex = queue.indexOfFirst { it.trackId == localTrack.id }.coerceAtLeast(0)
        ctx.uiState.update { state ->
            state.copy(
                musicPlaybackErrorMessage = null,
                musicTrackList = state.musicTrackList.map { row ->
                    if (row.trackId == track.trackId) row.copy(isDownloaded = true) else row
                },
                nowPlayingTitle = resolvedTrack.title,
            )
        }
        ctx.session.musicBookIdByTrackId = ctx.session.musicBookIdByTrackId + (resolvedTrack.id to bookId)
        ctx.session.musicLibraryTracks = libraryTracks
        ctx.musicPlaybackQueue.set(libraryTracks)
        val libraryStartIndex = libraryTracks.indexOfFirst { it.track.id == localTrack.id }.coerceAtLeast(0)
        ctx.session.lastPrefetchSourceTrackId = localTrack.id
        ctx.playbackClient.playQueue(queue, startIndex)
        prefetch.scheduleMusicPrefetch(libraryStartIndex + 1)
        ctx.localLibraryNotifier.notifyLocalLibraryChanged()
    }

    suspend fun buildLocalMusicQueueItems(
        libraryTracks: List<MusicLibraryTrack>,
        windowEntries: List<MusicLibraryTrack>,
        forcedLocalTrack: Track? = null,
    ): List<QueuePlayItem> {
        if (windowEntries.isEmpty()) return emptyList()
        return windowEntries.mapNotNull { entry ->
            val localTrack = if (entry.track.id == forcedLocalTrack?.id) {
                forcedLocalTrack
            } else {
                ctx.trackDownloadEnsurer.resolveLocalTrack(entry.book.id, entry.track)
            } ?: return@mapNotNull null
            val index = libraryTracks.indexOfFirst { it.track.id == entry.track.id }.coerceAtLeast(0)
            ctx.playbackQueueBuilder.itemForMusicLibraryTrack(
                entry = entry,
                localTrack = localTrack,
                indexInLibrary = index,
                librarySize = libraryTracks.size,
            )
        }
    }

    private suspend fun musicBookAvailable(bookId: String): Boolean {
        if (catalogLists.findKnownMusicBook(bookId) != null) return true
        return withContext(Dispatchers.IO) { ctx.catalogRepository.getBook(bookId) != null }
    }

    private fun isMusicEntryPlayable(entry: MusicLibraryTrack): Boolean {
        val hasLocal = !entry.track.localPath.isNullOrBlank()
        return MusicPlaybackAdvanceRules.isTrackPlayable(
            isDownloaded = hasLocal,
            isNetworkOnline = ctx.uiState.value.isNetworkOnline,
        )
    }

}
