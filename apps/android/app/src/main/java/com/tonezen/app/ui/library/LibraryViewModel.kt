package com.tonezen.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.R
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.network.NetworkMonitor
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
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicLibraryTrack
import com.tonezen.app.domain.music.MusicShuffleQueue
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
    private var musicLibraryTracks: List<MusicLibraryTrack> = emptyList()
    private var musicPrefetchJob: Job? = null
    private var lastPrefetchSourceTrackId: String? = null

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
                }
                _uiState.update {
                    it.copy(
                        nowPlayingTitle = snapshot.trackTitle ?: it.nowPlayingTitle,
                        musicPlayback = MusicPlaybackUi(
                            isActive = isMusic && trackId != null,
                            trackId = trackId,
                            trackTitle = snapshot.trackTitle,
                            artist = snapshot.artist,
                            albumTitle = snapshot.albumTitle,
                            bookId = trackId?.let { id -> musicBookIdByTrackId[id] },
                            isPlaying = snapshot.isPlaying && isMusic,
                        ),
                    )
                }
            }
        }
        viewModelScope.launch {
            playbackEvents.trackEnded.collect {
                handleMusicTrackEnded()
            }
        }
    }

    val filteredCycles: List<Cycle>
        get() {
            val state = _uiState.value
            return filterCycles(
                cycles = state.cycles,
                downloadedBookIds = state.downloadedBookIds,
                favoriteBookIds = state.favoriteBookIds,
                filter = state.filter,
            )
        }

    val filteredBooks: List<Book>
        get() {
            val state = _uiState.value
            return filterAndSortBooks(
                books = state.books,
                downloadedBookIds = state.downloadedBookIds,
                favoriteBookIds = state.favoriteBookIds,
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

    fun downloadMusicTrack(track: MusicListTrack) {
        if (track.isDownloaded || musicDownloadNotifier.state.value.isActive) return
        downloadTrackJob?.cancel()
        downloadTrackJob = viewModelScope.launch {
            downloadTrackOnly(track)
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
            val favorites = catalogRepository.getFavoriteBookIds()
            if (local.isEmpty()) {
                withContext(Dispatchers.Main) {
                    _uiState.update { it.copy(isLoadingCatalog = true) }
                }
            }
            if (local.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    updateCatalog(local, localCycles, favorites)
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
            updateCatalog(remoteBooks, remoteCycles, catalogRepository.getFavoriteBookIds())
        } finally {
            _uiState.update { it.copy(isLoadingCatalog = false) }
        }
    }

    private suspend fun updateCatalog(books: List<Book>, cycles: List<Cycle>, favorites: Set<String>) {
        updateBooks(books, favorites)
        _uiState.update { it.copy(cycles = cycles) }
    }

    private suspend fun updateBooks(books: List<Book>, favorites: Set<String>) {
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
                favoriteBookIds = favorites,
                musicTrackList = trackList,
            )
        }
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
            val favorites = withContext(Dispatchers.IO) {
                catalogRepository.getFavoriteBookIds()
            }
            _uiState.update {
                it.copy(
                    downloadedBookIds = downloaded,
                    favoriteBookIds = favorites,
                    musicTrackList = trackList,
                )
            }
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
}
