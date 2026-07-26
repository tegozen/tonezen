package com.tonezen.app.ui.library

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackQueueBuilder
import com.tonezen.app.playback.PlaybackSnapshot
import com.tonezen.app.playback.TrackDownloadQueueController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

internal class LibraryCycleHandler(
    uiState: MutableStateFlow<LibraryUiState>,
    scope: CoroutineScope,
    session: LibraryPlaybackSession,
    catalogRepository: CatalogRepository,
    downloadRepository: DownloadRepository,
    sessionRepository: SessionRepository,
    progressSyncRepository: ProgressSyncRepository,
    trackDownloadEnsurer: TrackDownloadEnsurer,
    downloadQueueController: TrackDownloadQueueController,
    downloadQueueNotifier: DownloadQueueNotifier,
    playbackClient: PlaybackClient,
    playbackQueueBuilder: PlaybackQueueBuilder,
    localLibraryNotifier: LocalLibraryNotifier,
    playbackErrorMessage: (EnsureTrackOutcome.Failure?) -> String,
) {
    private val ctx = LibraryCycleHandlerContext(
        uiState = uiState,
        scope = scope,
        session = session,
        catalogRepository = catalogRepository,
        downloadRepository = downloadRepository,
        sessionRepository = sessionRepository,
        progressSyncRepository = progressSyncRepository,
        trackDownloadEnsurer = trackDownloadEnsurer,
        downloadQueueController = downloadQueueController,
        downloadQueueNotifier = downloadQueueNotifier,
        playbackClient = playbackClient,
        playbackQueueBuilder = playbackQueueBuilder,
        localLibraryNotifier = localLibraryNotifier,
        playbackErrorMessage = playbackErrorMessage,
    )

    fun toggleCyclePlay(cycle: Cycle) = ctx.toggleCyclePlay(cycle)

    fun downloadCycle(cycle: Cycle) = ctx.downloadCycle(cycle)

    fun removeCycleDownloads(cycle: Cycle) = ctx.removeCycleDownloads(cycle)

    fun toggleCycleListened(cycle: Cycle) = ctx.toggleCycleListened(cycle)

    fun markCycleListened(cycle: Cycle) = ctx.markCycleListened(cycle)

    fun markCycleUnlistened(cycle: Cycle) = ctx.markCycleUnlistened(cycle)

    fun refreshCycleMenu(cycle: Cycle) = ctx.refreshCycleMenu(cycle)

    suspend fun refreshCycleCardStates(cycles: List<Cycle>, downloadedBookIds: Set<String>) =
        ctx.refreshCycleCardStates(cycles, downloadedBookIds)

    fun resolveCyclePlaybackUi(snapshot: PlaybackSnapshot): CyclePlaybackUi =
        ctx.resolveCyclePlaybackUi(snapshot)

    suspend fun onAudiobookSnapshot(snapshot: PlaybackSnapshot) = ctx.onAudiobookSnapshot(snapshot)

    fun flushActiveAudiobookProgress(snapshot: PlaybackSnapshot) =
        ctx.flushActiveAudiobookProgress(snapshot)

    fun handleAudiobookTrackEnded() = ctx.handleAudiobookTrackEnded()
}
