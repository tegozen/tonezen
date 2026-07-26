package com.tonezen.app.ui.music

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.MusicPlaybackQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackQueueBuilder
import com.tonezen.app.playback.PlaybackSnapshot
import com.tonezen.app.playback.TrackDownloadQueueController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow

internal class MusicHandler(
    uiState: MutableStateFlow<MusicUiState>,
    scope: CoroutineScope,
    session: MusicPlaybackSession,
    catalogRepository: CatalogRepository,
    downloadRepository: DownloadRepository,
    trackDownloadEnsurer: TrackDownloadEnsurer,
    downloadQueueController: TrackDownloadQueueController,
    downloadQueueNotifier: DownloadQueueNotifier,
    localLibraryNotifier: LocalLibraryNotifier,
    playbackClient: PlaybackClient,
    playbackQueueBuilder: PlaybackQueueBuilder,
    musicPlaybackQueue: MusicPlaybackQueue,
    playbackErrorMessage: (EnsureTrackOutcome.Failure?) -> String,
) {
    private val ctx = MusicHandlerContext(
        uiState = uiState,
        scope = scope,
        session = session,
        catalogRepository = catalogRepository,
        downloadRepository = downloadRepository,
        trackDownloadEnsurer = trackDownloadEnsurer,
        downloadQueueController = downloadQueueController,
        downloadQueueNotifier = downloadQueueNotifier,
        localLibraryNotifier = localLibraryNotifier,
        playbackClient = playbackClient,
        playbackQueueBuilder = playbackQueueBuilder,
        musicPlaybackQueue = musicPlaybackQueue,
        playbackErrorMessage = playbackErrorMessage,
    )
    private val catalogLists = MusicCatalogLists(ctx)
    private val prefetch = MusicPrefetch(ctx)
    private val playExecutor = MusicPlayExecutor(ctx, catalogLists, prefetch)
    private val downloadActions = MusicDownloadActions(ctx, catalogLists)
    private val uiActions = MusicUiActions(ctx, catalogLists, prefetch, playExecutor)

    init {
        prefetch.playExecutor = playExecutor
    }

    var onBulkDownloadFinished: () -> Unit
        get() = ctx.onBulkDownloadFinished
        set(value) {
            ctx.onBulkDownloadFinished = value
        }

    fun onMusicTabSelected() = uiActions.onMusicTabSelected()

    fun onNetworkOffline() = uiActions.onNetworkOffline()

    fun onMiniPlayerPlayPause() = uiActions.onMiniPlayerPlayPause()

    fun onMusicTrackClick(track: MusicListTrack) = uiActions.onMusicTrackClick(track)

    fun playMusicWave() = uiActions.playMusicWave()

    fun downloadMusicTrack(track: MusicListTrack) = downloadActions.downloadMusicTrack(track)

    fun cancelAllDownloads() = downloadActions.cancelAllDownloads()

    fun deleteMusicTrack(track: MusicListTrack) = downloadActions.deleteMusicTrack(track, prefetch)

    fun downloadAllMusic() = downloadActions.downloadAllMusic(prefetch)

    suspend fun onMusicSnapshot(snapshot: PlaybackSnapshot) = uiActions.onMusicSnapshot(snapshot)

    fun musicPlaybackUi(snapshot: PlaybackSnapshot): MusicPlaybackUi = uiActions.musicPlaybackUi(snapshot)

    fun isMusicSnapshot(snapshot: PlaybackSnapshot): Boolean = uiActions.isMusicSnapshot(snapshot)

    suspend fun invalidatePlaybackIfLocalFilesMissing() = uiActions.invalidatePlaybackIfLocalFilesMissing()

    fun handleMusicTrackEnded() = uiActions.handleMusicTrackEnded()

    suspend fun reloadMusicCatalogData() = catalogLists.reloadMusicCatalogData()

    suspend fun resolveDownloadedTrackIdsForUi(
        reconcileLocalPaths: Boolean = true,
    ): Set<String> = catalogLists.resolveDownloadedTrackIdsForUi(reconcileLocalPaths)

    suspend fun buildMusicTrackListForCatalogUpdate(
        rebuildMusic: Boolean = false,
        reconcileLocalPaths: Boolean = true,
    ): List<MusicListTrack> = catalogLists.buildMusicTrackListForCatalogUpdate(rebuildMusic, reconcileLocalPaths)

    suspend fun refreshMusicTrackListForDownloads(): List<MusicListTrack> =
        catalogLists.refreshMusicTrackListForDownloads()

    suspend fun refreshMusicTrackListWithDownloadedIds(downloadedTrackIds: Set<String>): List<MusicListTrack> =
        catalogLists.refreshMusicTrackListWithDownloadedIds(downloadedTrackIds)
}
