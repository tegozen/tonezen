package com.tonezen.app.ui.music

import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.downloads.DownloadPriority
import com.tonezen.app.domain.downloads.EnqueueDownloadRequest
import com.tonezen.app.playback.forMusic
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.update

internal class MusicDownloadActions(
    private val ctx: MusicHandlerContext,
    private val catalogLists: MusicCatalogLists,
) {
    private suspend fun resolveMusicDownloadBookId(track: MusicListTrack): String =
        ctx.catalogRepository.canonicalBookIdForTrack(track.trackId) ?: track.bookId

    private suspend fun musicEnqueueRequest(
        track: MusicListTrack,
        priority: DownloadPriority,
        batchId: String? = null,
    ): EnqueueDownloadRequest {
        val bookId = resolveMusicDownloadBookId(track)
        return EnqueueDownloadRequest(
            bookId = bookId,
            trackId = track.trackId,
            priority = priority,
            batchId = batchId,
            title = track.trackTitle,
            subtitle = track.artist,
            contentType = ContentType.MUSIC.name.lowercase(),
        )
    }

    private suspend fun catalogTrackNeedsDownload(track: MusicListTrack): Boolean {
        val catalogTrack = ctx.catalogRepository.findTrackInCatalog(track.trackId) ?: return false
        val bookId = catalogTrack.bookId
        if (!catalogTrack.localPath.isNullOrBlank() &&
            ctx.trackDownloadEnsurer.isTrackLocal(bookId, track.trackId)
        ) {
            return false
        }
        return !ctx.trackDownloadEnsurer.isTrackLocal(bookId, track.trackId)
    }

    fun downloadMusicTrack(track: MusicListTrack) {
        if (!ctx.uiState.value.isNetworkOnline) {
            ctx.reportMusicDownloadError(EnsureTrackOutcome.Failure.OFFLINE)
            return
        }
        ctx.scope.launch {
            if (!withContext(Dispatchers.IO) { catalogTrackNeedsDownload(track) }) {
                val updatedList = catalogLists.refreshMusicTrackListDownloadState(ctx.uiState.value.musicTrackList)
                ctx.uiState.update { it.copy(musicTrackList = updatedList) }
                return@launch
            }
            ctx.downloadQueueController.enqueue(
                withContext(Dispatchers.IO) {
                    musicEnqueueRequest(track, DownloadPriority.USER)
                },
            )
        }
    }

    fun cancelAllDownloads() {
        ctx.downloadQueueController.cancelAll()
    }

    fun deleteMusicTrack(track: MusicListTrack, prefetch: MusicPrefetch) {
        ctx.scope.launch {
            val bookId = withContext(Dispatchers.IO) { resolveMusicDownloadBookId(track) }
            val isPlaying = ctx.uiState.value.musicPlayback.trackId == track.trackId
            ctx.downloadQueueController.cancelTrack(bookId, track.trackId)
            if (isPlaying) {
                ctx.playJob?.cancel()
                prefetch.cancelPrefetchJobs()
                ctx.playbackClient.stopAndRelease()
            }
            withContext(Dispatchers.IO) {
                ctx.downloadRepository.deleteLocalTrack(bookId, track.trackId)
                ctx.catalogRepository.clearTrackLocalPath(bookId, track.trackId)
            }
            val updatedList = refreshMusicTrackListDownloadState(
                ctx.uiState.value.musicTrackList,
                withContext(Dispatchers.IO) { catalogLists.resolveDownloadedTrackIdsForUi() },
            )
            ctx.uiState.update { state ->
                state.copy(
                    musicTrackList = updatedList,
                    musicPlayback = if (isPlaying) MusicPlaybackUi() else state.musicPlayback,
                    musicPlaybackErrorMessage = null,
                )
            }
            ctx.localLibraryNotifier.notifyLocalLibraryChanged()
        }
    }

    fun downloadAllMusic(prefetch: MusicPrefetch) {
        if (!ctx.uiState.value.isNetworkOnline) {
            ctx.reportMusicDownloadError(EnsureTrackOutcome.Failure.OFFLINE)
            return
        }
        val snapshot = ctx.downloadQueueNotifier.snapshot().forMusic()
        if (snapshot.isBulkDownloading) {
            snapshot.activeBatchId?.let { ctx.downloadQueueController.cancelBatch(it) }
            ctx.lastBulkBatchId = null
            return
        }
        ctx.scope.launch {
            val missingTracks = withContext(Dispatchers.IO) {
                ctx.uiState.value.musicTrackList.filter { catalogTrackNeedsDownload(it) }
            }
            if (missingTracks.isEmpty()) {
                val updatedList = catalogLists.refreshMusicTrackListDownloadState(ctx.uiState.value.musicTrackList)
                ctx.uiState.update { it.copy(musicTrackList = updatedList) }
                return@launch
            }
            pauseMusicForBulkDownload(prefetch)
            val batchId = java.util.UUID.randomUUID().toString()
            ctx.lastBulkBatchId = batchId
            ctx.downloadQueueController.enqueueBatch(
                missingTracks.map { listTrack ->
                    musicEnqueueRequest(listTrack, DownloadPriority.BULK, batchId)
                },
                batchId,
            )
        }
    }

    private fun pauseMusicForBulkDownload(prefetch: MusicPrefetch) {
        ctx.playJob?.cancel()
        prefetch.cancelPrefetchJobs()
        if (ctx.uiState.value.musicPlayback.isActive) {
            ctx.playbackClient.pause()
        }
    }
}
