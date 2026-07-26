package com.tonezen.app.ui.music

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.domain.downloads.DownloadAwaitResult
import com.tonezen.app.domain.music.MusicDownloadInteractionState
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.MusicPlaybackQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackQueueBuilder
import com.tonezen.app.playback.TrackDownloadQueueController
import com.tonezen.app.playback.forMusic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

internal class MusicHandlerContext(
    val uiState: MutableStateFlow<MusicUiState>,
    val scope: CoroutineScope,
    val session: MusicPlaybackSession,
    val catalogRepository: CatalogRepository,
    val downloadRepository: DownloadRepository,
    val trackDownloadEnsurer: TrackDownloadEnsurer,
    val downloadQueueController: TrackDownloadQueueController,
    val downloadQueueNotifier: DownloadQueueNotifier,
    val localLibraryNotifier: LocalLibraryNotifier,
    val playbackClient: PlaybackClient,
    val playbackQueueBuilder: PlaybackQueueBuilder,
    val musicPlaybackQueue: MusicPlaybackQueue,
    val playbackErrorMessage: (EnsureTrackOutcome.Failure?) -> String,
) {
    var onBulkDownloadFinished: () -> Unit = {}
    var playJob: Job? = null
    var musicPrefetchJob: Job? = null
    var prefetchTargetIndex: Int = -1
    var lastBulkBatchId: String? = null

    fun musicDownloadInteractionState(): MusicDownloadInteractionState {
        val snapshot = downloadQueueNotifier.snapshot().forMusic()
        return MusicDownloadInteractionState(
            isTrackDownloading = snapshot.isTrackDownloading,
            isBulkDownloading = snapshot.isBulkDownloading,
            activeTrackId = snapshot.activeTrackId,
        )
    }

    fun reportMusicDownloadError(
        failure: EnsureTrackOutcome.Failure? = EnsureTrackOutcome.Failure.DOWNLOAD_FAILED,
    ) {
        uiState.update {
            it.copy(musicPlaybackErrorMessage = playbackErrorMessage(failure))
        }
    }

    fun reportMusicDownloadError(awaitResult: DownloadAwaitResult) {
        val failure = when (awaitResult) {
            DownloadAwaitResult.OFFLINE -> EnsureTrackOutcome.Failure.OFFLINE
            DownloadAwaitResult.FAILED,
            DownloadAwaitResult.CANCELLED,
            DownloadAwaitResult.COMPLETED,
            -> EnsureTrackOutcome.Failure.DOWNLOAD_FAILED
        }
        reportMusicDownloadError(failure)
    }
}
