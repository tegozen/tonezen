package com.tonezen.app.ui.library

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.CatalogSyncRepository
import com.tonezen.app.data.remote.ProgressSyncRepository
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
    private val progressSyncRepository: ProgressSyncRepository,
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
            var wasOnline = networkMonitor.isOnline()
            networkMonitor.online.collect { online ->
                uiState.update { it.copy(isNetworkOnline = online) }
                if (online && !wasOnline) {
                    val sessionData = sessionRepository.loadSession()
                    if (sessionData != null) {
                        try {
                            val refreshed = sessionRepository.refreshIfNeeded(sessionData)
                            if (refreshed != null) {
                                // Pull then flush — same as Desktop reconnect; never push before hydrate.
                                progressSyncRepository.pullAll(refreshed.accessToken)
                                progressSyncRepository.flushPending(refreshed.accessToken)
                            }
                        } catch (_: Exception) {
                        }
                    }
                }
                wasOnline = online
            }
        }
        scope.launch {
            progressSyncRepository.updates
                .debounce(LOCAL_LIBRARY_REFRESH_DEBOUNCE_MS)
                .collect { progress ->
                    val cycles = uiState.value.cycles.filter { cycle ->
                        cycle.books.any { it.id == progress.bookId }
                    }
                    if (cycles.isNotEmpty()) {
                        cycleHandler.refreshCycleCardStates(cycles, uiState.value.downloadedBookIds)
                    }
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
                    // Online login/reinstall: brief splash for bounded progress pull.
                    // Offline: do not force splash — local downloads must open immediately.
                    if (sessionData != null && networkMonitor.isOnline()) {
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
