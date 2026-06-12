package com.tonezen.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.R
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.library.LibraryContentFilter
import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.library.LibrarySortOrder
import com.tonezen.app.domain.library.filterAndSortBooks
import com.tonezen.app.domain.library.filterCycles
import com.tonezen.app.domain.model.AudiobookProgress
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.Cycle
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicLibraryTrack
import com.tonezen.app.domain.music.MusicShuffleQueue
import com.tonezen.app.domain.progress.isCycleFullyListened
import com.tonezen.app.domain.progress.orderedCycleEntriesFromResume
import com.tonezen.app.domain.progress.resolveCycleListenFraction
import com.tonezen.app.domain.progress.resolveCycleResumeTarget
import com.tonezen.app.playback.MusicDownloadNotifier
import com.tonezen.app.playback.MusicPlaybackQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackEvents
import com.tonezen.app.playback.PlaybackQueueBuilder
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val downloadRepository: DownloadRepository,
    private val sessionRepository: SessionRepository,
    private val progressSyncRepository: ProgressSyncRepository,
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

    private var musicBookIdByTrackId: Map<String, String> = emptyMap()
    private var musicCandidates: List<Pair<Book, Track>> = emptyList()
    private var musicStartedInSession = false
    private var playJob: Job? = null
    private var downloadTrackJob: Job? = null
    private var downloadAllJob: Job? = null
    private var cycleDownloadJob: Job? = null
    private var musicLibraryTracks: List<MusicLibraryTrack> = emptyList()
    private var musicPrefetchJob: Job? = null
    private var lastPrefetchSourceTrackId: String? = null
    private var activeAudiobookBookId: String? = null
    private var activeAudiobookTrackId: String? = null
    private var lastAudiobookProgressSaveMs = 0L

    init {
        playbackClient.connect()
        viewModelScope.launch {
            sessionRepository.session.collectLatest { session ->
                refreshSessionState(session)
                loadLibrary(session)
            }
        }
        viewModelScope.launch {
            localLibraryNotifier.changes.collect {
                invalidatePlaybackIfLocalFilesMissing()
                refreshDownloads()
            }
        }
        viewModelScope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
                val trackId = snapshot.trackId
                val isMusic = snapshot.contentType == ContentType.MUSIC ||
                    (trackId != null && trackId in musicBookIdByTrackId)
                if (isMusic && trackId != null) {
                    musicStartedInSession = true
                    if (musicLibraryTracks.isNotEmpty() && trackId != lastPrefetchSourceTrackId) {
                        lastPrefetchSourceTrackId = trackId
                        val index = musicLibraryTracks.indexOfFirst { it.track.id == trackId }
                        if (index >= 0) {
                            scheduleMusicPrefetch(index + 1)
                        }
                    }
                } else if (snapshot.contentType == ContentType.AUDIOBOOK) {
                    val audiobookTrackId = snapshot.trackId
                    if (audiobookTrackId != null) {
                        val bookId = withContext(Dispatchers.IO) {
                            catalogRepository.findBookForTrack(audiobookTrackId)?.id
                        }
                        if (bookId != null) {
                            activeAudiobookBookId = bookId
                            activeAudiobookTrackId = audiobookTrackId
                            if (snapshot.isPlaying) {
                                maybeSaveAudiobookProgress(bookId, audiobookTrackId, snapshot.positionMs)
                            }
                        }
                    }
                }
                _uiState.update { state ->
                    val cyclePlayback = when {
                        isMusic -> CyclePlaybackUi()
                        snapshot.contentType == ContentType.AUDIOBOOK && activeAudiobookBookId != null -> {
                            val cycleId = state.cycles.firstOrNull { cycle ->
                                cycle.books.any { it.id == activeAudiobookBookId }
                            }?.id
                            if (cycleId != null) {
                                val preparing = state.cyclePlayback.isPreparing &&
                                    state.cyclePlayback.cycleId == cycleId &&
                                    !snapshot.isPlaying
                                CyclePlaybackUi(
                                    cycleId = cycleId,
                                    isPlaying = snapshot.isPlaying,
                                    isPreparing = preparing,
                                    downloadProgress = if (preparing) {
                                        state.cyclePlayback.downloadProgress
                                    } else {
                                        null
                                    },
                                )
                            } else {
                                CyclePlaybackUi()
                            }
                        }
                        state.cyclePlayback.isPreparing -> state.cyclePlayback
                        else -> CyclePlaybackUi()
                    }
                    state.copy(
                        nowPlayingTitle = snapshot.trackTitle ?: state.nowPlayingTitle,
                        musicPlayback = MusicPlaybackUi(
                            isActive = isMusic && trackId != null,
                            trackId = trackId,
                            trackTitle = snapshot.trackTitle,
                            artist = snapshot.artist,
                            albumTitle = snapshot.albumTitle,
                            bookId = trackId?.let { id -> musicBookIdByTrackId[id] },
                            isPlaying = snapshot.isPlaying && isMusic,
                        ),
                        cyclePlayback = cyclePlayback,
                    )
                }
            }
        }
        viewModelScope.launch {
            playbackEvents.trackEnded.collect {
                handleMusicTrackEnded()
                handleAudiobookTrackEnded()
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
            )
        }

    val filteredBooks: List<Book>
        get() {
            val state = _uiState.value
            return filterAndSortBooks(
                books = state.books,
                downloadedBookIds = state.downloadedBookIds,
                filter = state.filter,
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

    fun onMusicTabSelected() {
        viewModelScope.launch {
            if (musicCandidates.isEmpty() || musicStartedInSession) return@launch
            if (_uiState.value.musicTrackList.isNotEmpty()) return@launch
            _uiState.update { it.copy(musicTrackList = buildMusicTrackList(shuffle = true)) }
        }
    }

    fun onMiniPlayerPlayPause() {
        val playback = _uiState.value.musicPlayback
        if (!playback.isActive || playback.trackId == null) {
            if (playback.isPlaying) playbackClient.pause() else playbackClient.play()
            return
        }
        val listedTrack = _uiState.value.musicTrackList.find { it.trackId == playback.trackId }
        if (listedTrack != null) {
            onMusicTrackClick(listedTrack)
            return
        }
        viewModelScope.launch {
            val track = resolvePlaybackTrack(playback) ?: return@launch
            onMusicTrackClick(track)
        }
    }

    fun onMusicTrackClick(track: MusicListTrack) {
        if (musicDownloadNotifier.state.value.isActive) return
        val playback = _uiState.value.musicPlayback
        if (playback.trackId == track.trackId && playback.isActive) {
            if (playback.isPlaying) {
                playbackClient.pause()
            } else if (!track.isDownloaded) {
                playJob?.cancel()
                musicDownloadNotifier.beginTrack(track.trackId)
                _uiState.update { it.copy(musicPlaybackErrorRes = null) }
                playJob = viewModelScope.launch {
                    playMusicTrack(track, showDownloadProgress = true)
                }
            } else {
                playbackClient.play()
            }
            return
        }
        playJob?.cancel()
        if (!track.isDownloaded) {
            musicDownloadNotifier.beginTrack(track.trackId)
        }
        _uiState.update { it.copy(musicPlaybackErrorRes = null) }
        playJob = viewModelScope.launch {
            playMusicTrack(track, showDownloadProgress = !track.isDownloaded)
        }
    }

    fun toggleCyclePlay(cycle: Cycle) {
        if (musicDownloadNotifier.state.value.isActive) return
        val cyclePlayback = _uiState.value.cyclePlayback
        if (cyclePlayback.isPreparing && cyclePlayback.cycleId == cycle.id) return

        val activeCycleId = activeAudiobookBookId?.let { bookId ->
            _uiState.value.cycles.firstOrNull { item ->
                item.books.any { it.id == bookId }
            }?.id
        }
        val snapshot = playbackClient.snapshot.value
        if (
            activeCycleId == cycle.id &&
            snapshot.trackId != null &&
            snapshot.contentType == ContentType.AUDIOBOOK
        ) {
            if (snapshot.isPlaying) {
                playbackClient.pause()
            } else {
                playbackClient.play()
            }
            return
        }
        playJob?.cancel()
        playJob = viewModelScope.launch {
            playCycleInternal(cycle)
        }
    }

    fun downloadCycle(cycle: Cycle) {
        if (cycleDownloadJob?.isActive == true || musicDownloadNotifier.state.value.isActive) return
        cycleDownloadJob = viewModelScope.launch {
            val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
                ?: return@launch
            val accessToken = session.accessToken
            withContext(Dispatchers.IO) {
                for (bookSlug in cycle.bookOrder) {
                    val book = cycle.books.find { it.slug == bookSlug } ?: continue
                    val tracks = catalogRepository.getTracksForBook(book.id)
                    tracks.filter { it.localPath.isNullOrBlank() }.forEach { track ->
                        val file = downloadRepository.downloadTrack(
                            accessToken,
                            book.id,
                            track.id,
                        ) { }
                        catalogRepository.markTrackDownloaded(book.id, track.id, file.absolutePath)
                    }
                }
            }
            localLibraryNotifier.notifyLocalLibraryChanged()
            refreshDownloadedBooks()
            refreshCycleCardStates(listOf(cycle), _uiState.value.downloadedBookIds)
        }
    }

    fun removeCycleDownloads(cycle: Cycle) {
        if (cycleDownloadJob?.isActive == true) return
        viewModelScope.launch {
            val activeBookId = activeAudiobookBookId
            if (activeBookId != null && cycle.books.any { it.id == activeBookId }) {
                playJob?.cancel()
                playbackClient.stopAndRelease()
                _uiState.update { it.copy(cyclePlayback = CyclePlaybackUi()) }
            }
            withContext(Dispatchers.IO) {
                for (book in cycle.books) {
                    catalogRepository.clearLocalDownloads(book.id)
                    val tracks = catalogRepository.getTracksForBook(book.id)
                    tracks.forEach { track ->
                        downloadRepository.deleteLocalTrack(book.id, track.id)
                    }
                }
            }
            localLibraryNotifier.notifyLocalLibraryChanged()
            refreshDownloadedBooks()
            refreshCycleCardStates(listOf(cycle), _uiState.value.downloadedBookIds)
        }
    }

    fun toggleCycleListened(cycle: Cycle) {
        viewModelScope.launch {
            val tracksByBookId = withContext(Dispatchers.IO) {
                cycle.books.associate { book ->
                    book.id to catalogRepository.getTracksForBook(book.id)
                }
            }
            val progressByBookId = withContext(Dispatchers.IO) {
                cycle.books.associate { book ->
                    book.id to catalogRepository.getProgress(book.id)
                }
            }
            if (isCycleFullyListened(cycle, tracksByBookId, progressByBookId)) {
                markCycleUnlistened(cycle)
            } else {
                markCycleListened(cycle)
            }
        }
    }

    fun markCycleListened(cycle: Cycle) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                for (bookSlug in cycle.bookOrder) {
                    val book = cycle.books.find { it.slug == bookSlug } ?: continue
                    val tracks = catalogRepository.getTracksForBook(book.id).sortedBy { it.sortOrder }
                    val lastTrack = tracks.lastOrNull() ?: continue
                    persistAudiobookProgress(book.id, lastTrack.id, lastTrack.durationMs ?: 0L)
                }
            }
            refreshCycleCardStates(listOf(cycle), _uiState.value.downloadedBookIds)
        }
    }

    fun markCycleUnlistened(cycle: Cycle) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                cycle.books.forEach { book ->
                    catalogRepository.clearProgress(book.id)
                }
            }
            refreshCycleCardStates(listOf(cycle), _uiState.value.downloadedBookIds)
        }
    }

    fun downloadMusicTrack(track: MusicListTrack) {
        if (track.isDownloaded || musicDownloadNotifier.state.value.isActive) return
        downloadTrackJob?.cancel()
        downloadTrackJob = viewModelScope.launch {
            downloadTrackOnly(track)
        }
    }

    fun deleteMusicTrack(track: MusicListTrack) {
        if (musicDownloadNotifier.state.value.isActive) return
        viewModelScope.launch {
            val isPlaying = _uiState.value.musicPlayback.trackId == track.trackId
            if (isPlaying) {
                playJob?.cancel()
                clearMusicPrefetchState()
                playbackClient.stopAndRelease()
                musicDownloadNotifier.clear()
            }
            withContext(Dispatchers.IO) {
                downloadRepository.deleteLocalTrack(track.bookId, track.trackId)
                catalogRepository.clearTrackLocalPath(track.bookId, track.trackId)
            }
            musicCandidates = musicCandidates.filterNot { it.second.id == track.trackId }
            musicLibraryTracks = musicLibraryTracks.filterNot { it.track.id == track.trackId }
            musicBookIdByTrackId = musicBookIdByTrackId - track.trackId
            _uiState.update { state ->
                state.copy(
                    musicTrackList = state.musicTrackList.filterNot { it.trackId == track.trackId },
                    musicPlayback = if (isPlaying) MusicPlaybackUi() else state.musicPlayback,
                    musicPlaybackErrorRes = null,
                )
            }
            localLibraryNotifier.notifyLocalLibraryChanged()
            refreshDownloadedBooks()
        }
    }

    fun downloadAllMusic() {
        if (musicDownloadNotifier.state.value.isActive) return
        val pending = _uiState.value.musicTrackList.filter { !it.isDownloaded }
        if (pending.isEmpty()) return
        downloadAllJob?.cancel()
        downloadAllJob = viewModelScope.launch {
            val total = _uiState.value.musicTrackList.size
            var completed = _uiState.value.musicTrackList.count { it.isDownloaded }
            musicDownloadNotifier.beginBulk(completed, total)
            for (item in pending) {
                val track = withContext(Dispatchers.IO) {
                    catalogRepository.getTracksForBook(item.bookId).find { it.id == item.trackId }
                } ?: continue
                val outcome = withContext(Dispatchers.IO) {
                    trackDownloadEnsurer.ensureTrackLocal(item.bookId, track) { trackProgress ->
                        viewModelScope.launch(Dispatchers.Main.immediate) {
                            musicDownloadNotifier.updateBulk(completed, total, item.trackId, trackProgress)
                        }
                    }
                }
                if (outcome.track != null) {
                    completed++
                    _uiState.update { state ->
                        state.copy(
                            musicTrackList = state.musicTrackList.map { row ->
                                if (row.trackId == item.trackId) row.copy(isDownloaded = true) else row
                            },
                        )
                    }
                    musicDownloadNotifier.incrementBulkDownloaded(completed, total)
                }
            }
            musicDownloadNotifier.clear()
            refreshDownloadedBooks()
        }
    }

    private fun refreshSessionState(session: StoredSession?) {
        _uiState.update {
            it.copy(sessionState = sessionRepository.resolveState(session))
        }
    }

    private suspend fun loadLibrary(session: StoredSession?) {
        withContext(Dispatchers.IO) {
            val refreshed = sessionRepository.refreshIfNeeded(session)
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
            refreshed?.let { progressSyncRepository.start(it) }
            if (networkMonitor.isOnline()) {
                refreshCatalogFromRemote(refreshed?.accessToken)
            } else {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoadingCatalog = false) }
                }
            }
        }
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
        refreshCycleCardStates(cycles, _uiState.value.downloadedBookIds)
    }

    private suspend fun updateBooks(books: List<Book>) {
        musicBookIdByTrackId = withContext(Dispatchers.IO) { buildMusicTrackBookMap(books) }
        rebuildMusicCandidates(books)
        val trackList = when {
            _uiState.value.musicTrackList.isNotEmpty() ->
                refreshMusicTrackListDownloadState(_uiState.value.musicTrackList)
            musicStartedInSession ->
                buildMusicTrackList(shuffle = false)
            else ->
                buildMusicTrackList(shuffle = true)
        }
        _uiState.update {
            it.copy(
                books = books,
                downloadedBookIds = catalogRepository.downloadedBookIds(books),
                musicTrackList = trackList,
            )
        }
    }

    fun refreshCycleMenu(cycle: Cycle) {
        viewModelScope.launch {
            refreshCycleCardStates(listOf(cycle), _uiState.value.downloadedBookIds)
        }
    }

    private suspend fun refreshCycleCardStates(cycles: List<Cycle>, downloadedBookIds: Set<String>) {
        val states = withContext(Dispatchers.IO) {
            cycles.associate { cycle ->
                val tracksByBookId = cycle.books.associate { book ->
                    book.id to catalogRepository.getTracksForBook(book.id)
                }
                val progressByBookId = cycle.books.associate { book ->
                    book.id to catalogRepository.getProgress(book.id)
                }
                val fraction = resolveCycleListenFraction(cycle, tracksByBookId, progressByBookId)
                val allTracks = tracksByBookId.values.flatten()
                val isFullyDownloaded = allTracks.isNotEmpty() &&
                    allTracks.all { !it.localPath.isNullOrBlank() }
                cycle.id to CycleCardState(
                    isDownloaded = isFullyDownloaded,
                    progressFraction = fraction?.takeIf { it > 0f },
                    showDownload = allTracks.any { it.localPath.isNullOrBlank() },
                    showRemoveDownload = allTracks.any { !it.localPath.isNullOrBlank() },
                    isListened = isCycleFullyListened(cycle, tracksByBookId, progressByBookId),
                )
            }
        }
        _uiState.update { it.copy(cycleCardStateById = states) }
    }

    private suspend fun invalidatePlaybackIfLocalFilesMissing() {
        val snapshot = playbackClient.snapshot.value
        val trackId = snapshot.trackId ?: return
        val isMusic = snapshot.contentType == ContentType.MUSIC || trackId in musicBookIdByTrackId
        if (!isMusic) return
        val bookId = musicBookIdByTrackId[trackId]
            ?: catalogRepository.findBookForTrack(trackId)?.id
            ?: return
        val isLocal = trackDownloadEnsurer.isTrackLocal(bookId, trackId)
        if (!isLocal) {
            playJob?.cancel()
            clearMusicPrefetchState()
            playbackClient.stopAndRelease()
            musicDownloadNotifier.clear()
            _uiState.update {
                it.copy(
                    musicPlayback = MusicPlaybackUi(),
                    musicPlaybackErrorRes = null,
                )
            }
        }
    }

    private fun clearMusicPrefetchState() {
        musicPrefetchJob?.cancel()
        musicPrefetchJob = null
        musicLibraryTracks = emptyList()
        lastPrefetchSourceTrackId = null
        musicPlaybackQueue.clear()
    }

    private fun scheduleMusicPrefetch(fromIndex: Int) {
        if (fromIndex !in musicLibraryTracks.indices) return
        if (musicDownloadNotifier.state.value.isBulkDownloading) return
        musicPrefetchJob?.cancel()
        musicPrefetchJob = viewModelScope.launch {
            prefetchMusicTrack(fromIndex)
        }
    }

    private suspend fun prefetchMusicTrack(index: Int) {
        if (index !in musicLibraryTracks.indices) return
        val entry = musicLibraryTracks[index]
        val bookId = entry.book.id
        val trackId = entry.track.id
        val alreadyQueued = withContext(Dispatchers.Main.immediate) {
            trackId in playbackClient.queuedTrackIds()
        }
        if (alreadyQueued) return

        val resolvedTrack = withContext(Dispatchers.IO) {
            catalogRepository.getTracksForBook(bookId).find { it.id == trackId } ?: entry.track
        }
        val needsDownload = withContext(Dispatchers.IO) {
            !trackDownloadEnsurer.isTrackLocal(bookId, trackId)
        }
        val progressReporter = if (needsDownload && !musicDownloadNotifier.state.value.isActive) {
            withContext(Dispatchers.Main.immediate) {
                musicDownloadNotifier.beginTrack(trackId)
            }
            createTrackProgressReporter(trackId)
        } else {
            null
        }
        val localTrack = withContext(Dispatchers.IO) {
            trackDownloadEnsurer.ensureTrackLocal(bookId, resolvedTrack, progressReporter).track
        } ?: run {
            if (progressReporter != null) {
                withContext(Dispatchers.Main.immediate) {
                    musicDownloadNotifier.finishTrack()
                }
            }
            return
        }
        if (progressReporter != null) {
            withContext(Dispatchers.Main.immediate) {
                musicDownloadNotifier.finishTrack()
            }
        }

        val queueItem = playbackQueueBuilder.itemForMusicLibraryTrack(
            entry = entry,
            localTrack = localTrack,
            indexInLibrary = index,
            librarySize = musicLibraryTracks.size,
        )
        withContext(Dispatchers.Main) {
            _uiState.update { state ->
                state.copy(
                    musicTrackList = state.musicTrackList.map { row ->
                        if (row.trackId == trackId) row.copy(isDownloaded = true) else row
                    },
                )
            }
            playbackClient.appendQueueItems(listOf(queueItem))
        }
        refreshDownloadedBooks()
    }

    private fun handleMusicTrackEnded() {
        if (musicDownloadNotifier.state.value.isActive) return
        val snapshot = playbackClient.snapshot.value
        val trackId = snapshot.trackId ?: return
        val isMusic = snapshot.contentType == ContentType.MUSIC || trackId in musicBookIdByTrackId
        if (!isMusic || musicLibraryTracks.isEmpty()) return
        val currentIndex = musicLibraryTracks.indexOfFirst { it.track.id == trackId }
        if (currentIndex < 0) return
        val nextIndex = MusicShuffleQueue.nextIndex(currentIndex, musicLibraryTracks.size)
        val nextEntry = musicLibraryTracks[nextIndex]
        val listTrack = _uiState.value.musicTrackList.find { it.trackId == nextEntry.track.id }
            ?: MusicListTrack(
                trackId = nextEntry.track.id,
                trackTitle = nextEntry.track.title,
                artist = nextEntry.book.author ?: nextEntry.book.title,
                albumTitle = nextEntry.book.title,
                bookId = nextEntry.book.id,
                durationMs = nextEntry.track.durationMs,
                isDownloaded = false,
            )
        playJob?.cancel()
        playJob = viewModelScope.launch {
            playMusicTrack(listTrack, showDownloadProgress = false)
        }
    }

    private suspend fun resolvePlaybackTrack(playback: MusicPlaybackUi): MusicListTrack? {
        val trackId = playback.trackId ?: return null
        val bookId = playback.bookId
            ?: catalogRepository.findBookForTrack(trackId)?.id
            ?: return null
        val domainTrack = catalogRepository.getTracksForBook(bookId).find { it.id == trackId }
            ?: return null
        val book = _uiState.value.books.find { it.id == bookId }
            ?: catalogRepository.findBookForTrack(trackId)
            ?: return null
        return MusicListTrack(
            trackId = trackId,
            trackTitle = playback.trackTitle ?: domainTrack.title,
            artist = playback.artist ?: book.author ?: book.title,
            albumTitle = playback.albumTitle ?: book.title,
            bookId = bookId,
            durationMs = domainTrack.durationMs,
            isDownloaded = trackDownloadEnsurer.isTrackLocal(bookId, trackId),
        )
    }

    fun refreshDownloads() {
        viewModelScope.launch {
            val books = _uiState.value.books
            val trackList = refreshMusicTrackListDownloadState(_uiState.value.musicTrackList)
            val downloaded = withContext(Dispatchers.IO) {
                catalogRepository.downloadedBookIds(books)
            }
            _uiState.update {
                it.copy(
                    downloadedBookIds = downloaded,
                    musicTrackList = trackList,
                )
            }
            refreshCycleCardStates(_uiState.value.cycles, downloaded)
        }
    }

    private suspend fun rebuildMusicCandidates(books: List<Book>) {
        musicCandidates = withContext(Dispatchers.IO) {
            catalogRepository.resolveMusicLibraryTracks().map { entry ->
                entry.book to entry.track
            }
        }
    }

    private suspend fun buildMusicTrackBookMap(books: List<Book>): Map<String, String> = buildMap {
        for (entry in catalogRepository.resolveMusicLibraryTracks()) {
            put(entry.track.id, entry.book.id)
        }
    }

    private suspend fun buildMusicTrackList(shuffle: Boolean): List<MusicListTrack> {
        if (musicCandidates.isEmpty()) return emptyList()
        val ordered = if (shuffle) musicCandidates.shuffled() else musicCandidates
        return withContext(Dispatchers.IO) {
            ordered.map { (book, track) -> toListTrack(book, track) }
        }
    }

    private suspend fun refreshMusicTrackListDownloadState(
        list: List<MusicListTrack>,
    ): List<MusicListTrack> = withContext(Dispatchers.IO) {
        list.map { item ->
            item.copy(isDownloaded = trackDownloadEnsurer.isTrackLocal(item.bookId, item.trackId))
        }
    }

    private suspend fun toListTrack(book: Book, track: Track): MusicListTrack = MusicListTrack(
        trackId = track.id,
        trackTitle = track.title,
        artist = book.author ?: book.title,
        albumTitle = book.title,
        bookId = book.id,
        durationMs = track.durationMs,
        isDownloaded = trackDownloadEnsurer.isTrackLocal(book.id, track.id),
    )

    private suspend fun buildMusicLibraryTracksFromList(): List<MusicLibraryTrack> {
        val list = _uiState.value.musicTrackList
        if (list.isEmpty()) {
            return withContext(Dispatchers.IO) {
                catalogRepository.resolveMusicLibraryTracks()
            }
        }
        val booksById = _uiState.value.books.associateBy { it.id }
        return withContext(Dispatchers.IO) {
            list.mapNotNull { item ->
                val book = booksById[item.bookId] ?: return@mapNotNull null
                val domainTrack = catalogRepository.getTracksForBook(book.id).find { it.id == item.trackId }
                    ?: return@mapNotNull null
                MusicLibraryTrack(book, domainTrack)
            }
        }
    }

    private suspend fun playMusicTrack(track: MusicListTrack, showDownloadProgress: Boolean) {
        val book = _uiState.value.books.find { it.id == track.bookId } ?: return
        val libraryTracks = buildMusicLibraryTracksFromList()
        val targetEntry = libraryTracks.find { it.track.id == track.trackId } ?: return
        val resolvedTrack = withContext(Dispatchers.IO) {
            catalogRepository.getTracksForBook(targetEntry.book.id).find { it.id == track.trackId }
        } ?: targetEntry.track
        val needsDownload = withContext(Dispatchers.IO) {
            resolvedTrack.localPath == null &&
                !trackDownloadEnsurer.isTrackLocal(targetEntry.book.id, resolvedTrack.id)
        }
        if (showDownloadProgress && !needsDownload) {
            musicDownloadNotifier.finishTrack()
        } else if (showDownloadProgress && needsDownload) {
            musicDownloadNotifier.beginTrack(track.trackId)
            yield()
        }
        val progressReporter = if (showDownloadProgress && needsDownload) {
            createTrackProgressReporter(track.trackId)
        } else {
            null
        }
        val outcome = withContext(Dispatchers.IO) {
            trackDownloadEnsurer.ensureTrackLocal(targetEntry.book.id, resolvedTrack, progressReporter)
        }
        val localTrack = outcome.track ?: run {
            musicDownloadNotifier.finishTrack()
            _uiState.update {
                it.copy(musicPlaybackErrorRes = playbackErrorRes(outcome.failure))
            }
            return
        }
        val queue = withContext(Dispatchers.IO) {
            playbackQueueBuilder.buildLocalMusicLibraryQueue(libraryTracks) { entry ->
                catalogRepository.getTracksForBook(entry.book.id)
                    .find { it.id == entry.track.id }
                    ?.takeIf { !it.localPath.isNullOrBlank() }
            }
        }
        if (queue.isEmpty()) return
        musicStartedInSession = true
        val startIndex = queue.indexOfFirst { it.trackId == localTrack.id }.coerceAtLeast(0)
        musicDownloadNotifier.finishTrack()
        _uiState.update { state ->
            state.copy(
                musicPlaybackErrorRes = null,
                musicTrackList = state.musicTrackList.map { row ->
                    if (row.trackId == track.trackId) row.copy(isDownloaded = true) else row
                },
                nowPlayingTitle = resolvedTrack.title,
            )
        }
        musicBookIdByTrackId = musicBookIdByTrackId + (resolvedTrack.id to targetEntry.book.id)
        musicLibraryTracks = libraryTracks
        musicPlaybackQueue.set(libraryTracks)
        val libraryStartIndex = libraryTracks.indexOfFirst { it.track.id == localTrack.id }.coerceAtLeast(0)
        lastPrefetchSourceTrackId = localTrack.id
        playbackClient.playQueue(queue, startIndex)
        scheduleMusicPrefetch(libraryStartIndex + 1)
        refreshDownloadedBooks()
    }

    private suspend fun downloadTrackOnly(track: MusicListTrack) {
        musicDownloadNotifier.beginTrack(track.trackId)
        _uiState.update { it.copy(musicPlaybackErrorRes = null) }
        val resolvedTrack = withContext(Dispatchers.IO) {
            catalogRepository.getTracksForBook(track.bookId).find { it.id == track.trackId }
        } ?: run {
            musicDownloadNotifier.finishTrack()
            return
        }
        val outcome = withContext(Dispatchers.IO) {
            trackDownloadEnsurer.ensureTrackLocal(
                track.bookId,
                resolvedTrack,
                createTrackProgressReporter(track.trackId),
            )
        }
        if (outcome.track != null) {
            musicDownloadNotifier.finishTrack()
            _uiState.update { state ->
                state.copy(
                    musicTrackList = state.musicTrackList.map { row ->
                        if (row.trackId == track.trackId) row.copy(isDownloaded = true) else row
                    },
                )
            }
            refreshDownloadedBooks()
        } else {
            musicDownloadNotifier.finishTrack()
            _uiState.update {
                it.copy(musicPlaybackErrorRes = playbackErrorRes(outcome.failure))
            }
        }
    }

    private fun createTrackProgressReporter(trackId: String): (Float) -> Unit {
        val reporter = object {
            var lastBucket = -1
        }
        return progress@{ progress ->
            val bucket = (progress * 50).toInt()
            if (bucket > reporter.lastBucket || progress >= 1f) {
                reporter.lastBucket = bucket
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    musicDownloadNotifier.updateTrack(trackId, progress)
                }
            }
        }
    }

    private suspend fun refreshDownloadedBooks() {
        val downloaded = withContext(Dispatchers.IO) {
            catalogRepository.downloadedBookIds(_uiState.value.books)
        }
        _uiState.update { it.copy(downloadedBookIds = downloaded) }
    }

    private fun playbackErrorRes(failure: EnsureTrackOutcome.Failure?): Int = when (failure) {
        EnsureTrackOutcome.Failure.OFFLINE -> R.string.music_playback_error_offline
        EnsureTrackOutcome.Failure.NO_SESSION -> R.string.music_playback_error_login
        EnsureTrackOutcome.Failure.DOWNLOAD_FAILED, null -> R.string.music_playback_error_download
    }

    private fun createCycleDownloadProgressReporter(cycleId: String): (Float) -> Unit {
        val reporter = object {
            var lastBucket = -1
        }
        return progress@{ progress ->
            val bucket = (progress * 50).toInt()
            if (bucket > reporter.lastBucket || progress >= 1f) {
                reporter.lastBucket = bucket
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    _uiState.update { state ->
                        if (state.cyclePlayback.cycleId != cycleId) return@update state
                        state.copy(
                            cyclePlayback = state.cyclePlayback.copy(downloadProgress = progress),
                        )
                    }
                }
            }
        }
    }

    private suspend fun playCycleInternal(cycle: Cycle) {
        _uiState.update {
            it.copy(
                cyclePlaybackErrorRes = null,
                cyclePlayback = CyclePlaybackUi(
                    cycleId = cycle.id,
                    isPreparing = true,
                    downloadProgress = 0f,
                ),
            )
        }
        val tracksByBookId = withContext(Dispatchers.IO) {
            cycle.books.associate { book ->
                book.id to catalogRepository.getTracksForBook(book.id)
            }
        }
        val progressByBookId = withContext(Dispatchers.IO) {
            cycle.books.associate { book ->
                book.id to catalogRepository.getProgress(book.id)
            }
        }
        val resume = resolveCycleResumeTarget(cycle, tracksByBookId, progressByBookId)
        if (resume == null) {
            _uiState.update {
                it.copy(
                    cyclePlayback = CyclePlaybackUi(),
                    cyclePlaybackErrorRes = R.string.cycle_playback_error_empty,
                )
            }
            return
        }
        val entries = orderedCycleEntriesFromResume(cycle, tracksByBookId, resume)
        if (entries.isEmpty()) {
            _uiState.update {
                it.copy(
                    cyclePlayback = CyclePlaybackUi(),
                    cyclePlaybackErrorRes = R.string.cycle_playback_error_empty,
                )
            }
            return
        }
        val needsDownload = withContext(Dispatchers.IO) {
            !trackDownloadEnsurer.isTrackLocal(resume.book.id, resume.track.id)
        }
        val progressReporter = if (needsDownload) {
            createCycleDownloadProgressReporter(cycle.id)
        } else {
            null
        }
        val startOutcome = withContext(Dispatchers.IO) {
            trackDownloadEnsurer.ensureTrackLocal(resume.book.id, resume.track, progressReporter)
        }
        val startTrack = startOutcome.track
        if (startTrack == null) {
            _uiState.update {
                it.copy(
                    cyclePlayback = CyclePlaybackUi(),
                    cyclePlaybackErrorRes = playbackErrorRes(startOutcome.failure),
                )
            }
            return
        }
        val queueResult = withContext(Dispatchers.IO) {
            playbackQueueBuilder.buildCycleQueue(entries, startTrack)
        }
        if (queueResult == null || queueResult.items.isEmpty()) {
            _uiState.update {
                it.copy(
                    cyclePlayback = CyclePlaybackUi(),
                    cyclePlaybackErrorRes = R.string.cycle_playback_error_empty,
                )
            }
            return
        }
        activeAudiobookBookId = resume.book.id
        activeAudiobookTrackId = resume.track.id
        _uiState.update {
            it.copy(
                nowPlayingTitle = startTrack.title,
                cyclePlaybackErrorRes = null,
                cyclePlayback = CyclePlaybackUi(cycleId = cycle.id),
            )
        }
        playbackClient.playQueue(
            queueResult.items,
            queueResult.startIndex,
            resume.startPositionMs,
        )
        refreshDownloadedBooks()
        refreshCycleCardStates(listOf(cycle), _uiState.value.downloadedBookIds)
    }

    private fun handleAudiobookTrackEnded() {
        val bookId = activeAudiobookBookId ?: return
        val trackId = activeAudiobookTrackId ?: return
        viewModelScope.launch {
            val track = withContext(Dispatchers.IO) {
                catalogRepository.getTracksForBook(bookId).find { it.id == trackId }
            } ?: return@launch
            persistAudiobookProgress(bookId, trackId, track.durationMs ?: 0L)
            refreshCycleCardStates(_uiState.value.cycles, _uiState.value.downloadedBookIds)
        }
    }

    private fun maybeSaveAudiobookProgress(bookId: String, trackId: String, positionMs: Long) {
        val now = System.currentTimeMillis()
        if (now - lastAudiobookProgressSaveMs < 15_000) return
        lastAudiobookProgressSaveMs = now
        viewModelScope.launch {
            persistAudiobookProgress(bookId, trackId, positionMs)
            refreshCycleCardStates(_uiState.value.cycles, _uiState.value.downloadedBookIds)
        }
    }

    private suspend fun persistAudiobookProgress(bookId: String, trackId: String, positionMs: Long) {
        val progress = AudiobookProgress(
            bookId = bookId,
            trackId = trackId,
            positionMs = positionMs,
            updatedAtEpochMs = System.currentTimeMillis(),
        )
        val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
        progressSyncRepository.saveLocal(progress, pendingSync = true, session?.accessToken)
    }
}
