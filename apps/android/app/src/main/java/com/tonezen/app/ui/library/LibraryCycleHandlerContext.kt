package com.tonezen.app.ui.library

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.playback.PlaybackCoordinator
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackQueueBuilder
import com.tonezen.app.playback.TrackDownloadQueueController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow

internal class LibraryCycleHandlerContext(
    val uiState: MutableStateFlow<LibraryUiState>,
    val scope: CoroutineScope,
    val session: LibraryPlaybackSession,
    val catalogRepository: CatalogRepository,
    val downloadRepository: DownloadRepository,
    val sessionRepository: SessionRepository,
    val progressSyncRepository: ProgressSyncRepository,
    val trackDownloadEnsurer: TrackDownloadEnsurer,
    val downloadQueueController: TrackDownloadQueueController,
    val downloadQueueNotifier: DownloadQueueNotifier,
    val playbackClient: PlaybackClient,
    val playbackQueueBuilder: PlaybackQueueBuilder,
    val localLibraryNotifier: LocalLibraryNotifier,
    val playbackErrorMessage: (EnsureTrackOutcome.Failure?) -> String,
) {
    val playbackCoordinator = PlaybackCoordinator()

    var cycleDownloadBatchId: String? = null
    var cyclePlayJob: Job? = null
}
