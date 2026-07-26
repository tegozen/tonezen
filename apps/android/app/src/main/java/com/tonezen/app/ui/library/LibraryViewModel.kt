package com.tonezen.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.CatalogSyncRepository
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.ProfileSyncRepository
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.library.LibraryContentFilter
import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.library.LibrarySortOrder
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.TrackDownloadQueueController
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackEvents
import com.tonezen.app.playback.PlaybackQueueBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

@HiltViewModel
class LibraryViewModel @Inject constructor(
    catalogRepository: CatalogRepository,
    catalogSyncRepository: CatalogSyncRepository,
    downloadRepository: DownloadRepository,
    sessionRepository: SessionRepository,
    progressSyncRepository: ProgressSyncRepository,
    profileSyncRepository: ProfileSyncRepository,
    networkMonitor: NetworkMonitor,
    playbackClient: PlaybackClient,
    playbackQueueBuilder: PlaybackQueueBuilder,
    trackDownloadEnsurer: TrackDownloadEnsurer,
    downloadQueueController: TrackDownloadQueueController,
    downloadQueueNotifier: DownloadQueueNotifier,
    localLibraryNotifier: LocalLibraryNotifier,
    playbackEvents: PlaybackEvents,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val session = LibraryPlaybackSession()
    private val cycleHandler = LibraryCycleHandler(
        uiState = _uiState,
        scope = viewModelScope,
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
        playbackErrorMessage = ::playbackErrorMessage,
    )
    private val catalogLoader = LibraryCatalogLoader(
        uiState = _uiState,
        scope = viewModelScope,
        catalogRepository = catalogRepository,
        catalogSyncRepository = catalogSyncRepository,
        sessionRepository = sessionRepository,
        progressSyncRepository = progressSyncRepository,
        profileSyncRepository = profileSyncRepository,
        networkMonitor = networkMonitor,
        cycleHandler = cycleHandler,
    )
    private val sessionObserver = LibrarySessionObserver(
        scope = viewModelScope,
        uiState = _uiState,
        session = session,
        cycleHandler = cycleHandler,
        catalogLoader = catalogLoader,
        sessionRepository = sessionRepository,
        catalogSyncRepository = catalogSyncRepository,
        progressSyncRepository = progressSyncRepository,
        networkMonitor = networkMonitor,
        playbackClient = playbackClient,
        playbackEvents = playbackEvents,
        downloadQueueNotifier = downloadQueueNotifier,
        localLibraryNotifier = localLibraryNotifier,
    )

    init {
        sessionObserver.start()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(filter = it.filter.copy(query = query)) }
    }

    fun setFilterSheetVisible(visible: Boolean) {
        _uiState.update { it.copy(showFilterSheet = visible) }
    }

    fun applyFilter(filter: LibraryFilterState) {
        _uiState.update { it.copy(filter = filter, showFilterSheet = false) }
    }

    fun resetFilter() {
        _uiState.update { it.copy(filter = LibraryFilterState()) }
    }

    fun setContentFilter(contentFilter: LibraryContentFilter) {
        _uiState.update { it.copy(filter = it.filter.copy(contentFilter = contentFilter)) }
    }

    fun setSortOrder(sortOrder: LibrarySortOrder) {
        _uiState.update { it.copy(filter = it.filter.copy(sortOrder = sortOrder)) }
    }

    fun toggleCyclePlay(cycle: Cycle) = cycleHandler.toggleCyclePlay(cycle)

    fun dismissCycleProgressSyncConflict() = cycleHandler.dismissCycleProgressSyncConflict()

    fun chooseCycleProgressSyncLocal() = cycleHandler.chooseCycleProgressSyncLocal()

    fun chooseCycleProgressSyncServer() = cycleHandler.chooseCycleProgressSyncServer()

    fun downloadCycle(cycle: Cycle) = cycleHandler.downloadCycle(cycle)

    fun removeCycleDownloads(cycle: Cycle) = cycleHandler.removeCycleDownloads(cycle)

    fun toggleCycleListened(cycle: Cycle) = cycleHandler.toggleCycleListened(cycle)

    fun markCycleListened(cycle: Cycle) = cycleHandler.markCycleListened(cycle)

    fun markCycleUnlistened(cycle: Cycle) = cycleHandler.markCycleUnlistened(cycle)

    fun clearCyclePlaybackError() {
        _uiState.update { it.copy(cyclePlaybackErrorMessage = null) }
    }

    fun refreshCycleMenu(cycle: Cycle) = cycleHandler.refreshCycleMenu(cycle)

    fun refreshDownloads() = catalogLoader.refreshDownloads()
}
