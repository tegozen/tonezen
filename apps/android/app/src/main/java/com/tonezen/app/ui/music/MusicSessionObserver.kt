package com.tonezen.app.ui.music

import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.CatalogSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.forMusic
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val LOCAL_LIBRARY_REFRESH_DEBOUNCE_MS = 300L

internal class MusicSessionObserver(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<MusicUiState>,
    private val musicHandler: MusicHandler,
    private val catalogLoader: MusicCatalogLoader,
    private val sessionRepository: SessionRepository,
    private val catalogSyncRepository: CatalogSyncRepository,
    private val networkMonitor: NetworkMonitor,
    private val playbackClient: PlaybackClient,
    private val playbackEvents: PlaybackEvents,
    private val downloadQueueNotifier: DownloadQueueNotifier,
    private val localLibraryNotifier: LocalLibraryNotifier,
) {
    private var catalogOwnerKey: String? = null
    private var pendingCatalogReload = false

    fun start() {
        musicHandler.onBulkDownloadFinished = {
            scope.launch {
                if (pendingCatalogReload) {
                    pendingCatalogReload = false
                    catalogLoader.reloadMusicCatalog()
                }
                catalogLoader.refreshDownloads()
            }
        }
        scope.launch {
            var wasQueueActive = downloadQueueNotifier.snapshot().isActive
            var wasBulkDownloading = downloadQueueNotifier.snapshot().forMusic().isBulkDownloading
            downloadQueueNotifier.state.collect { state ->
                val musicQueue = state.forMusic()
                if (!state.isActive && pendingCatalogReload) {
                    pendingCatalogReload = false
                    catalogLoader.reloadMusicCatalog()
                }
                if (wasQueueActive && !state.isActive) {
                    catalogLoader.refreshDownloads()
                }
                if (wasBulkDownloading && !musicQueue.isBulkDownloading) {
                    musicHandler.onBulkDownloadFinished()
                }
                wasQueueActive = state.isActive
                wasBulkDownloading = musicQueue.isBulkDownloading
            }
        }
        uiState.update { it.copy(isNetworkOnline = networkMonitor.isOnline()) }
        scope.launch {
            networkMonitor.online.collect { online ->
                val wasOnline = uiState.value.isNetworkOnline
                uiState.update { it.copy(isNetworkOnline = online) }
                if (wasOnline && !online) {
                    musicHandler.onNetworkOffline()
                }
            }
        }
        scope.launch {
            localLibraryNotifier.changes
                .debounce(LOCAL_LIBRARY_REFRESH_DEBOUNCE_MS)
                .collect {
                    catalogLoader.refreshDownloads()
                }
        }
        scope.launch {
            sessionRepository.session.collectLatest { sessionData ->
                val ownerKey = sessionData?.userId ?: "__anonymous__"
                if (ownerKey != catalogOwnerKey) {
                    catalogOwnerKey = ownerKey
                    catalogLoader.loadMusicLibrary(sessionData)
                }
            }
        }
        scope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
                val trackId = snapshot.trackId
                val isMusic = musicHandler.isMusicSnapshot(snapshot)
                if (isMusic && trackId != null) {
                    musicHandler.onMusicSnapshot(snapshot)
                }
                val musicPlayback = musicHandler.musicPlaybackUi(snapshot)
                val nowPlayingTitle = if (isMusic) {
                    snapshot.trackTitle ?: uiState.value.nowPlayingTitle
                } else {
                    uiState.value.nowPlayingTitle
                }
                uiState.update {
                    it.copy(
                        musicPlayback = musicPlayback,
                        nowPlayingTitle = nowPlayingTitle,
                    )
                }
            }
        }
        scope.launch {
            playbackEvents.trackEnded.collect {
                musicHandler.handleMusicTrackEnded()
            }
        }
        scope.launch {
            catalogSyncRepository.catalogUpdated.collect {
                if (downloadQueueNotifier.state.value.isActive) {
                    pendingCatalogReload = true
                } else {
                    catalogLoader.reloadMusicCatalog()
                }
            }
        }
    }
}
