package com.tonezen.app.ui.library

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.R
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
import com.tonezen.app.playback.MusicPlaybackQueue
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
    private val musicPlaybackQueue: MusicPlaybackQueue,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val session = LibraryPlaybackSession()
    private lateinit var cycleHandler: LibraryCycleHandler
    private lateinit var musicHandler: LibraryMusicHandler
    private var lastLibrarySnapshotUiKey: LibrarySnapshotUiKey? = null
    private var lastLoadedUserId: String? = null
    private var pendingCatalogReload = false

    init {
        cycleHandler = LibraryCycleHandler(
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
            cancelPlayJob = { musicHandler.cancelPlayJob() },
            playbackErrorRes = ::playbackErrorRes,
        )
        musicHandler = LibraryMusicHandler(
            uiState = _uiState,
            scope = viewModelScope,
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
            playbackErrorRes = ::playbackErrorRes,
            refreshCycleCardStates = { cycles, downloadedBookIds ->
                cycleHandler.refreshCycleCardStates(cycles, downloadedBookIds)
            },
        )
        musicHandler.onBulkDownloadFinished = {
            viewModelScope.launch {
                if (pendingCatalogReload) {
                    pendingCatalogReload = false
                    reloadCatalogFromLocal()
                }
                refreshDownloads()
            }
        }
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
                val wasOnline = _uiState.value.isNetworkOnline
                _uiState.update { it.copy(isNetworkOnline = online) }
                if (wasOnline && !online) {
                    musicHandler.onNetworkOffline()
                }
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
                val userId = sessionData?.userId
                if (userId != lastLoadedUserId) {
                    lastLoadedUserId = userId
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
                val trackId = snapshot.trackId
                val isMusic = musicHandler.isMusicSnapshot(snapshot)
                if (isMusic && trackId != null) {
                    musicHandler.onMusicSnapshot(snapshot)
                } else if (snapshot.contentType == ContentType.AUDIOBOOK) {
                    cycleHandler.onAudiobookSnapshot(snapshot)
                }
                val uiKey = LibrarySnapshotUiKey.from(snapshot)
                if (uiKey == lastLibrarySnapshotUiKey) return@collectLatest
                lastLibrarySnapshotUiKey = uiKey
                val current = _uiState.value
                val cyclePlayback = when {
                    isMusic -> CyclePlaybackUi()
                    snapshot.contentType == ContentType.AUDIOBOOK && session.activeAudiobookBookId != null ->
                        cycleHandler.resolveCyclePlaybackUi(snapshot)
                    current.cyclePlayback.isPreparing -> current.cyclePlayback
                    else -> CyclePlaybackUi()
                }
                val musicPlayback = musicHandler.musicPlaybackUi(snapshot)
                val nowPlayingTitle = snapshot.trackTitle ?: current.nowPlayingTitle
                _uiState.update { state ->
                    state.copy(
                        nowPlayingTitle = nowPlayingTitle,
                        musicPlayback = musicPlayback,
                        cyclePlayback = cyclePlayback,
                    )
                }
            }
        }
        viewModelScope.launch {
            playbackEvents.trackEnded.collect {
                musicHandler.handleMusicTrackEnded()
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

    fun onMusicTabSelected() = musicHandler.onMusicTabSelected()

    fun onMiniPlayerPlayPause() = musicHandler.onMiniPlayerPlayPause()

    fun playMusicWave() = musicHandler.playMusicWave()

    fun onMusicTrackClick(track: MusicListTrack) = musicHandler.onMusicTrackClick(track)

    fun toggleCyclePlay(cycle: Cycle) {
        cycleHandler.toggleCyclePlay(cycle) { job -> musicHandler.playJob = job }
    }

    fun downloadCycle(cycle: Cycle) = cycleHandler.downloadCycle(cycle)

    fun removeCycleDownloads(cycle: Cycle) = cycleHandler.removeCycleDownloads(cycle)

    fun toggleCycleListened(cycle: Cycle) = cycleHandler.toggleCycleListened(cycle)

    fun markCycleListened(cycle: Cycle) = cycleHandler.markCycleListened(cycle)

    fun markCycleUnlistened(cycle: Cycle) = cycleHandler.markCycleUnlistened(cycle)

    fun downloadMusicTrack(track: MusicListTrack) = musicHandler.downloadMusicTrack(track)

    fun deleteMusicTrack(track: MusicListTrack) = musicHandler.deleteMusicTrack(track)

    fun downloadAllMusic() = musicHandler.downloadAllMusic()

    fun cancelAllDownloads() = musicHandler.cancelAllDownloads()

    fun refreshCycleMenu(cycle: Cycle) = cycleHandler.refreshCycleMenu(cycle)

    fun refreshDownloads(reconcileLocalPaths: Boolean = true) {
        viewModelScope.launch {
            val books = _uiState.value.books
            val downloadedTrackIds = musicHandler.resolveDownloadedTrackIdsForUi(reconcileLocalPaths)
            val trackList = musicHandler.refreshMusicTrackListWithDownloadedIds(downloadedTrackIds)
            val downloaded = withContext(Dispatchers.IO) {
                catalogRepository.downloadedBookIds(books)
            }
            _uiState.update {
                it.copy(
                    downloadedBookIds = downloaded,
                    musicTrackList = trackList,
                )
            }
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
                        books = emptyList(),
                        cycles = emptyList(),
                        musicTrackList = emptyList(),
                        downloadedBookIds = emptySet(),
                    )
                }
            }
            return
        }
        val refreshed = withContext(Dispatchers.IO) {
            sessionRepository.refreshIfNeeded(sessionData)
        }
        refreshSessionState(refreshed)
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
                val localBooks = async(Dispatchers.IO) { catalogRepository.getAllBooks() }
                val localCycles = async(Dispatchers.IO) { catalogRepository.getAllCycles() }
                updateCatalog(
                    books = localBooks.await(),
                    cycles = localCycles.await(),
                    rebuildMusic = true,
                    reconcileLocalPaths = false,
                )
                if (_uiState.value.books.isNotEmpty() || _uiState.value.cycles.isNotEmpty()) {
                    _uiState.update { it.copy(isLoadingCatalog = false) }
                }
                try {
                    val (books, cycles) = remote.await()
                    updateCatalog(
                        books = books,
                        cycles = cycles,
                        rebuildMusic = true,
                        reconcileLocalPaths = true,
                    )
                } finally {
                    _uiState.update { it.copy(isLoadingCatalog = false) }
                }
            }
        } else {
            val local = withContext(Dispatchers.IO) { catalogRepository.getAllBooks() }
            val localCycles = withContext(Dispatchers.IO) { catalogRepository.getAllCycles() }
            updateCatalog(
                books = local,
                cycles = localCycles,
                rebuildMusic = true,
                reconcileLocalPaths = false,
            )
            _uiState.update { it.copy(isLoadingCatalog = false) }
            viewModelScope.launch(Dispatchers.IO) {
                catalogRepository.reconcileLocalDownloadPaths()
                withContext(Dispatchers.Main) {
                    refreshDownloads(reconcileLocalPaths = false)
                }
            }
        }
    }

    private suspend fun reloadCatalogFromLocal() {
        val books = withContext(Dispatchers.IO) { catalogRepository.getAllBooks() }
        val cycles = withContext(Dispatchers.IO) { catalogRepository.getAllCycles() }
        updateCatalog(books, cycles, rebuildMusic = true)
    }

    private suspend fun refreshCatalogFromRemote(
        accessToken: String?,
        rebuildMusic: Boolean = false,
    ) {
        try {
            val (books, cycles) = loadCatalogFromRemoteWithLocalFallback(catalogRepository, accessToken)
            updateCatalog(books, cycles, rebuildMusic)
        } finally {
            _uiState.update { it.copy(isLoadingCatalog = false) }
        }
    }

    private suspend fun updateCatalog(
        books: List<Book>,
        cycles: List<Cycle>,
        rebuildMusic: Boolean = false,
        reconcileLocalPaths: Boolean = true,
    ) {
        updateBooks(books, rebuildMusic, reconcileLocalPaths)
        _uiState.update { it.copy(cycles = cycles) }
        cycleHandler.refreshCycleCardStates(cycles, _uiState.value.downloadedBookIds)
    }

    private suspend fun updateBooks(
        books: List<Book>,
        rebuildMusic: Boolean = false,
        reconcileLocalPaths: Boolean = true,
    ) {
        musicHandler.reloadMusicCatalogData()
        val trackList = musicHandler.buildMusicTrackListForCatalogUpdate(
            rebuildMusic = rebuildMusic,
            reconcileLocalPaths = reconcileLocalPaths,
        )
        val downloaded = withContext(Dispatchers.IO) {
            catalogRepository.downloadedBookIds(books)
        }
        _uiState.update {
            it.copy(
                books = books,
                downloadedBookIds = downloaded,
                musicTrackList = trackList,
            )
        }
    }

    private fun playbackErrorRes(failure: EnsureTrackOutcome.Failure?): Int = when (failure) {
        EnsureTrackOutcome.Failure.OFFLINE -> R.string.music_playback_error_offline
        EnsureTrackOutcome.Failure.NO_SESSION -> R.string.music_playback_error_login
        EnsureTrackOutcome.Failure.DOWNLOAD_FAILED, null -> R.string.music_playback_error_download
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
