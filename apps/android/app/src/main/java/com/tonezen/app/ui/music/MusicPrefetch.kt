package com.tonezen.app.ui.music

import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicDownloadInteractionRules
import com.tonezen.app.domain.music.MusicLibraryTrack
import com.tonezen.app.domain.music.MusicQueueWindow
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.EnqueueDownloadRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class MusicPrefetch(
    private val ctx: MusicHandlerContext,
) {
    lateinit var playExecutor: MusicPlayExecutor

    fun clearMusicPrefetchState() {
        ctx.musicPrefetchJob?.cancel()
        ctx.musicPrefetchJob = null
        ctx.prefetchTargetIndex = -1
        ctx.session.musicLibraryTracks = emptyList()
        ctx.session.lastPrefetchSourceTrackId = null
        ctx.musicPlaybackQueue.clear()
    }

    fun cancelPrefetchJobs() {
        ctx.musicPrefetchJob?.cancel()
        ctx.musicPrefetchJob = null
        ctx.prefetchTargetIndex = -1
    }

    fun activeMusicLibraryTracks(): List<MusicLibraryTrack> =
        ctx.session.musicLibraryTracks.ifEmpty { ctx.musicPlaybackQueue.get() }

    fun scheduleMusicPrefetch(fromIndex: Int) {
        if (MusicDownloadInteractionRules.blocksPlaybackAdvanceDuringBulk(ctx.musicDownloadInteractionState())) {
            return
        }
        if (!ctx.uiState.value.isNetworkOnline) return
        if (fromIndex !in ctx.session.musicLibraryTracks.indices) return
        if (ctx.prefetchTargetIndex == fromIndex && ctx.musicPrefetchJob?.isActive == true) return
        ctx.prefetchTargetIndex = fromIndex
        ctx.musicPrefetchJob?.cancel()
        ctx.musicPrefetchJob = ctx.scope.launch {
            try {
                prefetchMusicTrack(fromIndex)
            } finally {
                if (ctx.prefetchTargetIndex == fromIndex) {
                    ctx.prefetchTargetIndex = -1
                }
            }
        }
    }

    private suspend fun prefetchMusicTrack(index: Int) {
        if (index !in ctx.session.musicLibraryTracks.indices) return
        val entry = ctx.session.musicLibraryTracks[index]
        val bookId = entry.book.id
        val trackId = entry.track.id
        val alreadyQueued = withContext(Dispatchers.Main.immediate) {
            trackId in ctx.playbackClient.queuedTrackIds()
        }
        if (alreadyQueued) return
        if (withContext(Dispatchers.IO) { ctx.trackDownloadEnsurer.isTrackLocal(bookId, trackId) }) {
            val localTrack = withContext(Dispatchers.IO) {
                ctx.trackDownloadEnsurer.resolveLocalTrack(bookId, entry.track)
            } ?: return
            withContext(Dispatchers.Main) {
                ctx.uiState.update { state ->
                    state.copy(
                        musicTrackList = state.musicTrackList.map { row ->
                            if (row.trackId == trackId) row.copy(isDownloaded = true) else row
                        },
                    )
                }
                appendPrefetchedQueueItem(trackId, localTrack)
            }
            return
        }
        val resolvedTrack = withContext(Dispatchers.IO) {
            ctx.catalogRepository.getTracksForBook(bookId).find { it.id == trackId } ?: entry.track
        }
        ctx.downloadQueueController.enqueue(
            EnqueueDownloadRequest(
                bookId = bookId,
                trackId = trackId,
                priority = DownloadPriority.PREFETCH,
                title = resolvedTrack.title,
                subtitle = entry.book.title,
                contentType = ContentType.MUSIC.name.lowercase(),
            ),
        )
    }

    private suspend fun appendPrefetchedQueueItem(trackId: String, localTrack: Track) {
        val index = ctx.session.musicLibraryTracks.indexOfFirst { it.track.id == trackId }
        if (index < 0) return
        val entry = ctx.session.musicLibraryTracks[index]
        val alreadyQueued = withContext(Dispatchers.Main.immediate) {
            trackId in ctx.playbackClient.queuedTrackIds()
        }
        if (alreadyQueued) return
        val queueItem = ctx.playbackQueueBuilder.itemForMusicLibraryTrack(
            entry = entry,
            localTrack = localTrack,
            indexInLibrary = index,
            librarySize = ctx.session.musicLibraryTracks.size,
        )
        withContext(Dispatchers.Main) {
            ctx.playbackClient.appendQueueItems(listOf(queueItem))
        }
    }

    suspend fun appendMusicQueueWindowIfNeeded(libraryTracks: List<MusicLibraryTrack>) {
        val shouldAppend = withContext(Dispatchers.Main.immediate) {
            ctx.playbackClient.shouldAppendQueueItems()
        }
        if (!shouldAppend) return
        val queuedIds = withContext(Dispatchers.Main.immediate) {
            ctx.playbackClient.queuedTrackIds()
        }
        val lastQueuedTrackId = withContext(Dispatchers.Main.immediate) {
            ctx.playbackClient.lastQueuedTrackId()
        } ?: return
        val windowEntries = MusicQueueWindow.appendWindow(
            items = libraryTracks,
            lastMaterializedTrackId = lastQueuedTrackId,
            materializedTrackIds = queuedIds,
            idOf = { it.track.id },
        )
        if (windowEntries.isEmpty()) return
        val queueItems = withContext(Dispatchers.IO) {
            playExecutor.buildLocalMusicQueueItems(libraryTracks, windowEntries)
        }
        withContext(Dispatchers.Main.immediate) {
            ctx.playbackClient.appendQueueItems(queueItems)
        }
    }
}
