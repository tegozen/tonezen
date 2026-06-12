package com.tonezen.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.R
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.library.LibraryContentFilter
import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.library.LibrarySortOrder
import com.tonezen.app.domain.library.filterAndSortBooks
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.domain.model.Track
import com.tonezen.app.domain.music.MusicLibraryTrack
import com.tonezen.app.domain.music.MusicShuffleQueue
import com.tonezen.app.playback.PlaybackClient
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
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    private var musicBookIdByTrackId: Map<String, String> = emptyMap()
    private var musicCandidates: List<Pair<Book, Track>> = emptyList()
    private var prefetchJob: Job? = null
    private var playJob: Job? = null

    init {
        playbackClient.connect()
        viewModelScope.launch {
            sessionRepository.session.collectLatest { session ->
                refreshSessionState(session)
                loadLibrary(session)
            }
        }
        viewModelScope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
                val trackId = snapshot.trackId
                val isMusic = snapshot.contentType == ContentType.MUSIC ||
                    (trackId != null && trackId in musicBookIdByTrackId)
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
            if (_uiState.value.musicPlayback.isPlaying) return@launch
            _uiState.update {
                it.copy(
                    musicPreview = pickRandomMusicPreview(
                        excludeTrackId = it.musicPlayback.trackId.takeIf { _ ->
                            it.musicPlayback.isActive
                        },
                    ),
                )
            }
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
            val favorites = catalogRepository.getFavoriteBookIds()
            if (local.isNotEmpty()) {
                withContext(Dispatchers.Main) {
                    updateBooks(local, favorites)
                }
            }
            if (networkMonitor.isOnline()) {
                refreshed?.let { progressSyncRepository.start(it) }
                syncCatalog(refreshed?.accessToken)
            }
        }
    }

    private suspend fun syncCatalog(accessToken: String?) {
        val remoteBooks = catalogRepository.syncFromRemote(accessToken)
        updateBooks(remoteBooks, catalogRepository.getFavoriteBookIds())
    }

    private suspend fun updateBooks(books: List<Book>, favorites: Set<String>) {
        musicBookIdByTrackId = withContext(Dispatchers.IO) { buildMusicTrackBookMap(books) }
        rebuildMusicCandidates(books)
        _uiState.update {
            it.copy(
                books = books,
                downloadedBookIds = catalogRepository.downloadedBookIds(books),
                favoriteBookIds = favorites,
                musicPreview = it.musicPreview ?: pickRandomMusicPreview(),
            )
        }
    }

    fun refreshDownloads() {
        viewModelScope.launch {
            val books = _uiState.value.books
            _uiState.update {
                it.copy(
                    downloadedBookIds = catalogRepository.downloadedBookIds(books),
                    favoriteBookIds = catalogRepository.getFavoriteBookIds(),
                )
            }
        }
    }

    fun toggleMusicPlayback() {
        if (_uiState.value.musicDownloadProgress != null) return
        val playback = _uiState.value.musicPlayback
        val preview = _uiState.value.musicPreview
        val hasLoadedTrack = playback.isActive ||
            (playback.trackId != null && playback.trackId == preview?.trackId)
        if (hasLoadedTrack) {
            if (playback.isPlaying) {
                playbackClient.pause()
            } else {
                playbackClient.play()
            }
            return
        }
        val trackPreview = preview ?: return
        playJob?.cancel()
        _uiState.update { it.copy(musicDownloadProgress = 0f, musicPlaybackErrorRes = null) }
        playJob = viewModelScope.launch { playMusicTrack(trackPreview, showDownloadProgress = true) }
    }

    fun shuffleMusicPreview() {
        if (_uiState.value.musicDownloadProgress != null) return
        prefetchJob?.cancel()
        viewModelScope.launch {
            val excludeId = _uiState.value.musicPlayback.takeIf { it.isActive }?.trackId
            _uiState.update {
                it.copy(musicPreview = pickRandomMusicPreview(excludeTrackId = excludeId), musicPlaybackErrorRes = null)
            }
        }
    }

    private fun schedulePrefetch(excludeTrackId: String) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            val preview = pickRandomMusicPreview(excludeTrackId = excludeTrackId) ?: return@launch
            withContext(Dispatchers.Main) {
                _uiState.update { it.copy(musicPreview = preview) }
            }
            if (trackDownloadEnsurer.isTrackLocal(preview.bookId, preview.trackId)) return@launch
            val book = _uiState.value.books.find { it.id == preview.bookId } ?: return@launch
            val track = catalogRepository.getTracksForBook(book.id).find { it.id == preview.trackId } ?: return@launch
            trackDownloadEnsurer.ensureTrackLocal(book.id, track)
            withContext(Dispatchers.Main) { refreshDownloadedBooks() }
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

    private suspend fun pickRandomMusicPreview(excludeTrackId: String? = null): MusicTrackPreview? {
        if (musicCandidates.isEmpty()) return null
        val pool = if (excludeTrackId != null && musicCandidates.size > 1) {
            musicCandidates.filter { it.second.id != excludeTrackId }
        } else {
            musicCandidates
        }
        if (pool.isEmpty()) return null
        val (book, track) = pool.random()
        return toPreview(book, track)
    }

    private fun toPreview(book: Book, track: Track) = MusicTrackPreview(
        trackId = track.id,
        trackTitle = track.title,
        artist = book.author ?: book.title,
        albumTitle = book.title,
        bookId = book.id,
    )

    private suspend fun playMusicTrack(preview: MusicTrackPreview, showDownloadProgress: Boolean) {
        val book = _uiState.value.books.find { it.id == preview.bookId } ?: return
        val libraryTracks = withContext(Dispatchers.IO) {
            val catalog = catalogRepository.resolveMusicLibraryTracks()
            MusicShuffleQueue.order(catalog, preview.trackId)
        }
        val targetEntry = libraryTracks.find { it.track.id == preview.trackId } ?: return
        val track = withContext(Dispatchers.IO) {
            catalogRepository.getTracksForBook(targetEntry.book.id).find { it.id == preview.trackId }
        } ?: targetEntry.track
        val needsDownload = withContext(Dispatchers.IO) {
            track.localPath == null && !trackDownloadEnsurer.isTrackLocal(targetEntry.book.id, track.id)
        }
        if (showDownloadProgress && !needsDownload) {
            _uiState.update { it.copy(musicDownloadProgress = null) }
        } else if (showDownloadProgress && needsDownload) {
            _uiState.update { it.copy(musicDownloadProgress = 0f) }
            yield()
        }
        val progressReporter = if (showDownloadProgress && needsDownload) {
            createMainProgressReporter()
        } else {
            null
        }
        val outcome = withContext(Dispatchers.IO) {
            trackDownloadEnsurer.ensureTrackLocal(targetEntry.book.id, track, progressReporter)
        }
        val localTrack = outcome.track ?: run {
            _uiState.update {
                it.copy(
                    musicDownloadProgress = null,
                    musicPlaybackErrorRes = playbackErrorRes(outcome.failure),
                )
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
        val startIndex = queue.indexOfFirst { it.trackId == localTrack.id }.coerceAtLeast(0)
        _uiState.update {
            it.copy(
                musicDownloadProgress = null,
                musicPlaybackErrorRes = null,
                musicPreview = preview,
                nowPlayingTitle = track.title,
            )
        }
        musicBookIdByTrackId = musicBookIdByTrackId + (track.id to targetEntry.book.id)
        playbackClient.playQueue(queue, startIndex)
        refreshDownloadedBooks()
        schedulePrefetch(excludeTrackId = track.id)
    }

    private fun createMainProgressReporter(): (Float) -> Unit {
        val reporter = object {
            var lastBucket = -1
        }
        return progress@{ progress ->
            val bucket = (progress * 50).toInt()
            if (bucket > reporter.lastBucket || progress >= 1f) {
                reporter.lastBucket = bucket
                viewModelScope.launch(Dispatchers.Main.immediate) {
                    _uiState.update { it.copy(musicDownloadProgress = progress.coerceIn(0f, 1f)) }
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
