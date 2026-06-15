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
import com.tonezen.app.domain.library.filterAndSortBooks
import com.tonezen.app.domain.library.filterCycles
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.playback.MusicDownloadNotifier
import com.tonezen.app.playback.MusicPlaybackQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackEvents
import com.tonezen.app.playback.PlaybackQueueBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    private val musicDownloadNotifier: MusicDownloadNotifier,
    private val localLibraryNotifier: LocalLibraryNotifier,
    private val playbackEvents: PlaybackEvents,
    private val musicPlaybackQueue: MusicPlaybackQueue,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private val session = LibraryPlaybackSession()
    private lateinit var cycleHandler: LibraryCycleHandler
    private lateinit var musicHandler: LibraryMusicHandler

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
            playbackClient = playbackClient,
            playbackQueueBuilder = playbackQueueBuilder,
            localLibraryNotifier = localLibraryNotifier,
            musicDownloadActive = { musicDownloadNotifier.state.value.isActive },
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
            musicDownloadNotifier = musicDownloadNotifier,
            localLibraryNotifier = localLibraryNotifier,
            playbackClient = playbackClient,
            playbackQueueBuilder = playbackQueueBuilder,
            musicPlaybackQueue = musicPlaybackQueue,
            playbackErrorRes = ::playbackErrorRes,
            refreshCycleCardStates = { cycles, downloadedBookIds ->
                cycleHandler.refreshCycleCardStates(cycles, downloadedBookIds)
            },
        )
        playbackClient.connect()
        ProcessLifecycleOwner.get().lifecycle.addObserver(
            object : DefaultLifecycleObserver {
                override fun onStop(owner: LifecycleOwner) {
                    cycleHandler.flushActiveAudiobookProgress(playbackClient.snapshot.value)
                }
            },
        )
        viewModelScope.launch {
            sessionRepository.session.collectLatest { sessionData ->
                refreshSessionState(sessionData)
                loadLibrary(sessionData)
            }
        }
        viewModelScope.launch {
            localLibraryNotifier.changes.collect {
                musicHandler.invalidatePlaybackIfLocalFilesMissing()
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
                if (
                    musicPlayback == current.musicPlayback &&
                    cyclePlayback == current.cyclePlayback &&
                    nowPlayingTitle == current.nowPlayingTitle
                ) {
                    return@collectLatest
                }
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
                reloadCatalogFromLocal()
            }
        }
    }

    val filteredCycles: List<Cycle>
        get() {
            val state = _uiState.value
            return filterCycles(
                cycles = state.cycles,
                downloadedBookIds = state.downloadedBookIds,
                filter = state.filter,
                progressUpdatedAtByBookId = state.progressUpdatedAtByBookId,
            )
        }

    val filteredBooks: List<Book>
        get() {
            val state = _uiState.value
            return filterAndSortBooks(
                books = state.books,
                downloadedBookIds = state.downloadedBookIds,
                filter = state.filter,
                progressUpdatedAtByBookId = state.progressUpdatedAtByBookId,
            )
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

    fun refreshCycleMenu(cycle: Cycle) = cycleHandler.refreshCycleMenu(cycle)

    fun refreshDownloads() {
        viewModelScope.launch {
            val books = _uiState.value.books
            val trackList = musicHandler.refreshMusicTrackListForDownloads()
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
        withContext(Dispatchers.IO) {
            val refreshed = sessionRepository.refreshIfNeeded(sessionData)
            withContext(Dispatchers.Main) {
                refreshSessionState(refreshed)
            }
            val local = catalogRepository.getAllBooks()
            val localCycles = catalogRepository.getAllCycles()
            if (local.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoadingCatalog = true) }
                }
            }
            if (local.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    updateCatalog(local, localCycles)
                    _uiState.update { it.copy(isLoadingCatalog = false) }
                }
            }
            refreshed?.let {
                progressSyncRepository.start(it)
                profileSyncRepository.start(it)
                catalogSyncRepository.start(it)
            } ?: catalogSyncRepository.stop()
            if (networkMonitor.isOnline()) {
                refreshCatalogFromRemote(refreshed?.accessToken)
            } else {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoadingCatalog = false) }
                }
            }
        }
    }

    private suspend fun reloadCatalogFromLocal() {
        val books = withContext(Dispatchers.IO) { catalogRepository.getAllBooks() }
        val cycles = withContext(Dispatchers.IO) { catalogRepository.getAllCycles() }
        updateCatalog(books, cycles)
    }

    private suspend fun refreshCatalogFromRemote(accessToken: String?) {
        try {
            val remoteBooks = catalogRepository.syncFromRemote(accessToken)
            val remoteCycles = catalogRepository.getAllCycles()
            updateCatalog(remoteBooks, remoteCycles)
        } finally {
            _uiState.update { it.copy(isLoadingCatalog = false) }
        }
    }

    private suspend fun updateCatalog(books: List<Book>, cycles: List<Cycle>) {
        updateBooks(books)
        _uiState.update { it.copy(cycles = cycles) }
        cycleHandler.refreshCycleCardStates(cycles, _uiState.value.downloadedBookIds)
    }

    private suspend fun updateBooks(books: List<Book>) {
        musicHandler.reloadMusicCatalogData()
        val trackList = musicHandler.buildMusicTrackListForCatalogUpdate()
        _uiState.update {
            it.copy(
                books = books,
                downloadedBookIds = catalogRepository.downloadedBookIds(books),
                musicTrackList = trackList,
            )
        }
    }

    private fun playbackErrorRes(failure: EnsureTrackOutcome.Failure?): Int = when (failure) {
        EnsureTrackOutcome.Failure.OFFLINE -> R.string.music_playback_error_offline
        EnsureTrackOutcome.Failure.NO_SESSION -> R.string.music_playback_error_login
        EnsureTrackOutcome.Failure.DOWNLOAD_FAILED, null -> R.string.music_playback_error_download
    }
}
