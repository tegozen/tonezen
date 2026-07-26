package com.tonezen.app.ui.library

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.CatalogSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

internal const val LOCAL_LIBRARY_REFRESH_DEBOUNCE_MS = 300L

internal class LibrarySessionObserver(
    private val scope: CoroutineScope,
    private val uiState: MutableStateFlow<LibraryUiState>,
    private val session: LibraryPlaybackSession,
    private val cycleHandler: LibraryCycleHandler,
    private val catalogLoader: LibraryCatalogLoader,
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
    private var lastLibrarySnapshotUiKey: LibrarySnapshotUiKey? = null

    fun start() {
        scope.launch {
            var wasQueueActive = downloadQueueNotifier.snapshot().isActive
            downloadQueueNotifier.state.collect { state ->
                if (!state.isActive && pendingCatalogReload) {
                    pendingCatalogReload = false
                    catalogLoader.reloadCatalogFromLocal()
                }
                if (wasQueueActive && !state.isActive) {
                    catalogLoader.refreshDownloads()
                }
                wasQueueActive = state.isActive
            }
        }
        uiState.update { it.copy(isNetworkOnline = networkMonitor.isOnline()) }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    cycleHandler.flushActiveAudiobookProgress(playbackClient.snapshot.value)
                }
            },
        )
        scope.launch {
            networkMonitor.online.collect { online ->
                uiState.update { it.copy(isNetworkOnline = online) }
            }
        }
        scope.launch {
            sessionRepository.isLoaded.collectLatest { loaded ->
                uiState.update { it.copy(isSessionLoaded = loaded) }
            }
        }
        scope.launch {
            sessionRepository.session.collectLatest { sessionData ->
                val ownerKey = sessionData?.userId ?: "__anonymous__"
                if (ownerKey != catalogOwnerKey) {
                    catalogOwnerKey = ownerKey
                    // Keep splash until loadLibrary finishes progress pull — otherwise
                    // AppShell is interactive on empty local DB and can LWW-wipe server.
                    if (sessionData != null) {
                        uiState.update { it.copy(isBootstrapComplete = false) }
                    }
                    catalogLoader.loadLibrary(sessionData)
                } else {
                    catalogLoader.refreshSessionState(sessionData)
                }
                if (sessionRepository.resolveState(sessionData) != SessionState.UNAUTHENTICATED) {
                    playbackClient.connect()
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
            playbackClient.snapshot.collectLatest { snapshot ->
                if (snapshot.contentType == ContentType.AUDIOBOOK) {
                    cycleHandler.onAudiobookSnapshot(snapshot)
                }
                val uiKey = LibrarySnapshotUiKey.from(snapshot)
                if (uiKey == lastLibrarySnapshotUiKey) return@collectLatest
                lastLibrarySnapshotUiKey = uiKey
                val current = uiState.value
                val cyclePlayback = when {
                    snapshot.contentType == ContentType.AUDIOBOOK && session.activeAudiobookBookId != null ->
                        cycleHandler.resolveCyclePlaybackUi(snapshot)
                    current.cyclePlayback.isPreparing -> current.cyclePlayback
                    else -> CyclePlaybackUi()
                }
                val nowPlayingTitle = snapshot.trackTitle ?: current.nowPlayingTitle
                uiState.update { state ->
                    state.copy(
                        nowPlayingTitle = nowPlayingTitle,
                        cyclePlayback = cyclePlayback,
                    )
                }
            }
        }
        scope.launch {
            playbackEvents.trackEnded.collect {
                cycleHandler.handleAudiobookTrackEnded()
            }
        }
        scope.launch {
            catalogSyncRepository.catalogUpdated.collect {
                if (downloadQueueNotifier.state.value.isActive) {
                    pendingCatalogReload = true
                } else {
                    catalogLoader.reloadCatalogFromLocal()
                }
            }
        }
    }
}
