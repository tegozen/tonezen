package com.tonezen.app.ui.library

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
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
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.TrackDownloadQueueController
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackEvents
import com.tonezen.app.playback.PlaybackQueueBuilder
import com.tonezen.app.playback.PlaybackSnapshot
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val catalogSyncRepository: CatalogSyncRepository,
    private val downloadRepository: DownloadRepository,
    private val sessionRepository: SessionRepository,
    private val progressSyncRepository: ProgressSyncRepository,
    private val profileSyncRepository: ProfileSyncRepository,
    private val networkMonitor: NetworkMonitor,
    private val playbackClient: PlaybackClient,
    private val playbackQueueBuilder: PlaybackQueueBuilder,
    private val trackDownloadEnsurer: TrackDownloadEnsurer,
    private val downloadQueueController: TrackDownloadQueueController,
    private val downloadQueueNotifier: DownloadQueueNotifier,
    private val localLibraryNotifier: LocalLibraryNotifier,
    private val playbackEvents: PlaybackEvents,
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
    private var lastLibrarySnapshotUiKey: LibrarySnapshotUiKey? = null
    private var catalogOwnerKey: String? = null
    private var pendingCatalogReload = false

    init {
        viewModelScope.launch {
            var wasQueueActive = downloadQueueNotifier.snapshot().isActive
            downloadQueueNotifier.state.collect { state ->
                if (!state.isActive && pendingCatalogReload) {
                    pendingCatalogReload = false
                    reloadCatalogFromLocal()
                }
                if (wasQueueActive && !state.isActive) {
                    refreshDownloads()
                }
                wasQueueActive = state.isActive
            }
        }
        _uiState.update { it.copy(isNetworkOnline = networkMonitor.isOnline()) }
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    cycleHandler.flushActiveAudiobookProgress(playbackClient.snapshot.value)
                }
            },
        )
        viewModelScope.launch {
            networkMonitor.online.collect { online ->
                _uiState.update { it.copy(isNetworkOnline = online) }
            }
        }
        viewModelScope.launch {
            sessionRepository.isLoaded.collectLatest { loaded ->
                _uiState.update { it.copy(isSessionLoaded = loaded) }
            }
        }
        viewModelScope.launch {
            sessionRepository.session.collectLatest { sessionData ->
                refreshSessionState(sessionData)
                if (sessionRepository.resolveState(sessionData) != SessionState.UNAUTHENTICATED) {
                    playbackClient.connect()
                }
                val ownerKey = sessionData?.userId ?: "__anonymous__"
                if (ownerKey != catalogOwnerKey) {
                    catalogOwnerKey = ownerKey
                    loadLibrary(sessionData)
                }
            }
        }
        viewModelScope.launch {
            localLibraryNotifier.changes
                .debounce(LOCAL_LIBRARY_REFRESH_DEBOUNCE_MS)
                .collect {
                refreshDownloads()
            }
        }
        viewModelScope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
                if (snapshot.contentType == ContentType.AUDIOBOOK) {
                    cycleHandler.onAudiobookSnapshot(snapshot)
                }
                val uiKey = LibrarySnapshotUiKey.from(snapshot)
                if (uiKey == lastLibrarySnapshotUiKey) return@collectLatest
                lastLibrarySnapshotUiKey = uiKey
                val current = _uiState.value
                val cyclePlayback = when {
                    snapshot.contentType == ContentType.AUDIOBOOK && session.activeAudiobookBookId != null ->
                        cycleHandler.resolveCyclePlaybackUi(snapshot)
                    current.cyclePlayback.isPreparing -> current.cyclePlayback
                    else -> CyclePlaybackUi()
                }
                val nowPlayingTitle = snapshot.trackTitle ?: current.nowPlayingTitle
                _uiState.update { state ->
                    state.copy(
                        nowPlayingTitle = nowPlayingTitle,
                        cyclePlayback = cyclePlayback,
                    )
                }
            }
        }
        viewModelScope.launch {
            playbackEvents.trackEnded.collect {
                cycleHandler.handleAudiobookTrackEnded()
            }
        }
        viewModelScope.launch {
            catalogSyncRepository.catalogUpdated.collect {
                if (downloadQueueNotifier.state.value.isActive) {
                    pendingCatalogReload = true
                } else {
                    reloadCatalogFromLocal()
                }
            }
        }
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

    fun downloadCycle(cycle: Cycle) = cycleHandler.downloadCycle(cycle)

    fun removeCycleDownloads(cycle: Cycle) = cycleHandler.removeCycleDownloads(cycle)

    fun toggleCycleListened(cycle: Cycle) = cycleHandler.toggleCycleListened(cycle)

    fun markCycleListened(cycle: Cycle) = cycleHandler.markCycleListened(cycle)

    fun markCycleUnlistened(cycle: Cycle) = cycleHandler.markCycleUnlistened(cycle)

    fun clearCyclePlaybackError() {
        _uiState.update { it.copy(cyclePlaybackErrorMessage = null) }
    }

    fun refreshCycleMenu(cycle: Cycle) = cycleHandler.refreshCycleMenu(cycle)

    fun refreshDownloads() {
        viewModelScope.launch {
            val books = _uiState.value.books
            val downloaded = withContext(Dispatchers.IO) {
                catalogRepository.downloadedBookIds(books)
            }
            _uiState.update { it.copy(downloadedBookIds = downloaded) }
            cycleHandler.refreshCycleCardStates(_uiState.value.cycles, downloaded)
        }
    }

    private fun refreshSessionState(sessionData: StoredSession?) {
        _uiState.update {
            it.copy(sessionState = sessionRepository.resolveState(sessionData))
        }
    }

    private suspend fun loadLibrary(sessionData: StoredSession?) {
        if (sessionData == null) {
            catalogSyncRepository.stop()
            withContext(Dispatchers.Main) {
                refreshSessionState(null)
                _uiState.update {
                    it.copy(
                        isLoadingCatalog = false,
                        isBootstrapComplete = true,
                        hasShownInitialLocalCatalog = false,
                        books = emptyList(),
                        cycles = emptyList(),
                        downloadedBookIds = emptySet(),
                    )
                }
            }
            return
        }
        val refreshed = withContext(Dispatchers.IO) {
            if (networkMonitor.isOnline()) {
                sessionRepository.refreshIfNeeded(sessionData)
            } else {
                sessionData
            }
        }
        refreshSessionState(refreshed)
        if (networkMonitor.isOnline()) {
            refreshed?.accessToken?.let { token ->
                withContext(Dispatchers.IO) {
                    progressSyncRepository.pullAll(token)
                }
            }
        }
        refreshed?.let {
            progressSyncRepository.start(it)
            profileSyncRepository.start(it)
            catalogSyncRepository.start(it)
        } ?: catalogSyncRepository.stop()

        if (networkMonitor.isOnline()) {
            _uiState.update { it.copy(isLoadingCatalog = true) }
            coroutineScope {
                val remote = async(Dispatchers.IO) {
                    loadCatalogFromRemoteWithLocalFallback(catalogRepository, refreshed?.accessToken)
                }
                val localCatalog = async(Dispatchers.IO) {
                    loadLocalCatalogProgressively(catalogRepository)
                }
                val (localBooks, localCycles) = localCatalog.await()
                updateCatalog(
                    books = localBooks,
                    cycles = localCycles,
                    markInitialLocalCatalogShown = localBooks.isNotEmpty() || localCycles.isNotEmpty(),
                )
                _uiState.update { it.copy(isBootstrapComplete = true) }
                if (_uiState.value.books.isNotEmpty() || _uiState.value.cycles.isNotEmpty()) {
                    _uiState.update { it.copy(isLoadingCatalog = false) }
                }
                try {
                    val (books, cycles) = remote.await()
                    updateCatalog(
                        books = books,
                        cycles = cycles,
                    )
                } finally {
                    _uiState.update { it.copy(isLoadingCatalog = false) }
                }
            }
        } else {
            val (local, localCycles) = withContext(Dispatchers.IO) {
                loadLocalCatalogProgressively(catalogRepository)
            }
            updateCatalog(
                books = local,
                cycles = localCycles,
                markInitialLocalCatalogShown = local.isNotEmpty() || localCycles.isNotEmpty(),
            )
            _uiState.update { it.copy(isLoadingCatalog = false, isBootstrapComplete = true) }
            viewModelScope.launch(Dispatchers.IO) {
                catalogRepository.reconcileLocalDownloadPaths()
                withContext(Dispatchers.Main) {
                    refreshDownloads()
                }
            }
        }
    }

    private suspend fun reloadCatalogFromLocal() {
        val (books, cycles) = withContext(Dispatchers.IO) {
            loadLocalCatalogProgressively(catalogRepository)
        }
        updateCatalog(books, cycles)
    }

    private suspend fun updateCatalog(
        books: List<Book>,
        cycles: List<Cycle>,
        markInitialLocalCatalogShown: Boolean = false,
    ) {
        val shouldPreserveCurrentCatalog =
            _uiState.value.hasShownInitialLocalCatalog &&
                _uiState.value.books.isNotEmpty() &&
                _uiState.value.cycles.isNotEmpty() &&
                books.isEmpty() &&
                cycles.isEmpty()
        if (shouldPreserveCurrentCatalog) return

        updateBooks(books, markInitialLocalCatalogShown)
        _uiState.update {
            it.copy(
                cycles = cycles,
                hasShownInitialLocalCatalog = it.hasShownInitialLocalCatalog || markInitialLocalCatalogShown,
            )
        }
        cycleHandler.refreshCycleCardStates(cycles, _uiState.value.downloadedBookIds)
    }

    private suspend fun updateBooks(
        books: List<Book>,
        markInitialLocalCatalogShown: Boolean = false,
    ) {
        val downloaded = withContext(Dispatchers.IO) {
            catalogRepository.downloadedBookIds(books)
        }
        _uiState.update {
            it.copy(
                books = books,
                downloadedBookIds = downloaded,
                hasShownInitialLocalCatalog = it.hasShownInitialLocalCatalog || markInitialLocalCatalogShown,
            )
        }
    }

    private fun playbackErrorMessage(failure: EnsureTrackOutcome.Failure?): String = when (failure) {
        EnsureTrackOutcome.Failure.OFFLINE -> "Нет сети — нужен интернет для первой загрузки"
        EnsureTrackOutcome.Failure.NO_SESSION -> "Войдите в аккаунт, чтобы скачать трек"
        EnsureTrackOutcome.Failure.DOWNLOAD_FAILED, null -> "Не удалось скачать трек"
    }

    private companion object {
        const val LOCAL_LIBRARY_REFRESH_DEBOUNCE_MS = 300L
    }
}

private data class LibrarySnapshotUiKey(
    val trackId: String?,
    val isPlaying: Boolean,
    val contentType: ContentType?,
    val trackTitle: String?,
    val artist: String?,
    val albumTitle: String?,
) {
    companion object {
        fun from(snapshot: PlaybackSnapshot) = LibrarySnapshotUiKey(
            trackId = snapshot.trackId,
            isPlaying = snapshot.isPlaying,
            contentType = snapshot.contentType,
            trackTitle = snapshot.trackTitle,
            artist = snapshot.artist,
            albumTitle = snapshot.albumTitle,
        )
    }
}
