package com.tonezen.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.AudiobookProgressEntity
import com.tonezen.app.data.local.BookEntity
import com.tonezen.app.data.local.CatalogDao
import com.tonezen.app.data.local.FavoriteEntity
import com.tonezen.app.data.local.TrackEntity
import com.tonezen.app.data.remote.AuthRepository
import com.tonezen.app.data.remote.ApiClient
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.data.remote.isNetworkAvailable
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.ContentType
import com.tonezen.app.domain.model.SessionState
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.domain.model.Track
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackEvents
import com.tonezen.app.playback.PlaybackMetadata
import com.tonezen.app.playback.QueuePlayItem
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogDao: CatalogDao,
    private val sessionRepository: SessionRepository,
    private val authRepository: AuthRepository,
    private val apiClient: ApiClient,
    private val downloadRepository: DownloadRepository,
    private val progressSyncRepository: ProgressSyncRepository,
    private val playbackClient: PlaybackClient,
    private val playbackEvents: PlaybackEvents,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var session: StoredSession? = null
    private var currentTrack: Track? = null
    private var lastProgressSaveMs = 0L

    init {
        session = sessionRepository.loadSession()
        refreshSessionState()
        loadLibrary()
        viewModelScope.launch {
            progressSyncRepository.updates.collect { progress ->
                val selected = _uiState.value.selectedBook
                if (selected?.id == progress.bookId) {
                    val track = catalogDao.getTracksForBook(selected.id).find { it.id == progress.trackId }
                    _uiState.update {
                        it.copy(progressLabel = track?.title?.let { title -> "Continue: $title" })
                    }
                }
            }
        }
        viewModelScope.launch {
            playbackEvents.trackEnded.collect { onPlaybackEnded() }
        }
        viewModelScope.launch {
            playbackClient.activeTrackId.collect { trackId ->
                val track = _uiState.value.tracks.find { it.id == trackId } ?: return@collect
                currentTrack = track
                _uiState.update { it.copy(nowPlayingTitle = track.title) }
            }
        }
        viewModelScope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
                _uiState.update {
                    it.copy(
                        isPlaying = snapshot.isPlaying,
                        nowPlayingTitle = snapshot.trackTitle ?: it.nowPlayingTitle,
                    )
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                if (playbackClient.isPlaying()) {
                    maybeSaveProgress(playbackClient.currentPositionMs())
                }
            }
        }
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                val signedIn = authRepository.signInWithPassword(email, password)
                sessionRepository.saveSession(signedIn)
                session = signedIn
                refreshSessionState()
                progressSyncRepository.start(signedIn)
                syncCatalog()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun logout() {
        progressSyncRepository.stop()
        playbackClient.stopAndRelease()
        currentTrack = null
        sessionRepository.clearSession()
        session = null
        refreshSessionState()
    }

    fun selectBook(book: Book) {
        viewModelScope.launch {
            val tracks = catalogDao.getTracksForBook(book.id).map { it.toDomain() }
            val progress = catalogDao.getProgress(book.id)
            _uiState.update {
                it.copy(
                    selectedBook = book,
                    tracks = tracks,
                    progressLabel = progress?.let { p ->
                        tracks.find { t -> t.id == p.trackId }?.title
                    },
                )
            }
        }
    }

    fun clearSelection() {
        playbackClient.pause()
        currentTrack = null
        _uiState.update {
            it.copy(
                selectedBook = null,
                tracks = emptyList(),
                progressLabel = null,
                nowPlayingTitle = null,
                isPlaying = false,
            )
        }
    }

    fun playBook() {
        val book = _uiState.value.selectedBook ?: return
        viewModelScope.launch {
            val tracks = catalogDao.getTracksForBook(book.id).map { it.toDomain() }
            val downloaded = tracks.filter { it.localPath != null }.sortedBy { it.sortOrder }
            if (downloaded.isEmpty()) return@launch
            val progress = catalogDao.getProgress(book.id)
            val track = progress?.let { p -> downloaded.find { it.id == p.trackId } } ?: downloaded.first()
            val startMs = if (track.id == progress?.trackId) progress.positionMs else 0L
            startBookPlayback(book, downloaded, track, startMs)
        }
    }

    fun pausePlayback() {
        playbackClient.pause()
    }

    fun resumePlayback() {
        playbackClient.play()
    }

    fun downloadBook() {
        val book = _uiState.value.selectedBook ?: return
        val token = session?.accessToken ?: return
        viewModelScope.launch {
            try {
                session = sessionRepository.refreshIfNeeded(session)
                val accessToken = session?.accessToken ?: return@launch
                val tracks = catalogDao.getTracksForBook(book.id)
                tracks.forEachIndexed { index, track ->
                    val file = downloadRepository.downloadTrack(
                        accessToken,
                        book.id,
                        track.id,
                    ) { progress ->
                        _uiState.update {
                            it.copy(downloadProgress = (index + progress) / tracks.size)
                        }
                    }
                    catalogDao.upsertTracks(
                        listOf(track.copy(localPath = file.absolutePath)),
                    )
                }
                _uiState.update { it.copy(downloadProgress = null) }
                selectBook(book)
                _uiState.update { it.copy(downloadedBookIds = it.downloadedBookIds + book.id) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, downloadProgress = null) }
            }
        }
    }

    fun deleteLocalDownloads() {
        val book = _uiState.value.selectedBook ?: return
        viewModelScope.launch {
            catalogDao.clearLocalPathsForBook(book.id)
            val tracks = catalogDao.getTracksForBook(book.id)
            tracks.forEach { track ->
                downloadRepository.deleteLocalTrack(book.id, track.id)
            }
            selectBook(book)
            _uiState.update { it.copy(downloadedBookIds = it.downloadedBookIds - book.id) }
        }
    }

    fun toggleFavorite() {
        val book = _uiState.value.selectedBook ?: return
        viewModelScope.launch {
            val existing = catalogDao.getFavorites().any { it.bookId == book.id }
            if (existing) catalogDao.deleteFavorite(book.id)
            else catalogDao.upsertFavorite(FavoriteEntity(book.id, pendingSync = true))
        }
    }

    fun saveAudiobookProgress(bookId: String, trackId: String, positionMs: Long) {
        viewModelScope.launch {
            val entity = AudiobookProgressEntity(
                bookId = bookId,
                trackId = trackId,
                positionMs = positionMs,
                updatedAtEpochMs = System.currentTimeMillis(),
                pendingSync = true,
            )
            session = sessionRepository.refreshIfNeeded(session)
            val token = session?.accessToken
            progressSyncRepository.saveLocal(entity, token)
        }
    }

    private fun refreshSessionState() {
        _uiState.update {
            it.copy(sessionState = sessionRepository.resolveState(session))
        }
    }

    private fun loadLibrary() {
        viewModelScope.launch {
            session = sessionRepository.refreshIfNeeded(session)
            refreshSessionState()
            val local = catalogDao.getAllBooks().map { it.toDomain() }
            if (local.isNotEmpty()) {
                _uiState.update {
                    it.copy(
                        books = local,
                        downloadedBookIds = loadDownloadedBookIds(local),
                    )
                }
            }
            if (context.isNetworkAvailable() && session != null) {
                session?.let { progressSyncRepository.start(it) }
                syncCatalog()
            }
        }
    }

    private suspend fun syncCatalog() {
        session = sessionRepository.refreshIfNeeded(session) ?: return
        val token = session?.accessToken
        session?.accessToken?.let { progressSyncRepository.updateAuth(it) }
        val remoteBooks = apiClient.fetchBooks(token)
        catalogDao.upsertBooks(
            remoteBooks.map { b ->
                BookEntity(b.id, b.slug, b.contentType.name.lowercase(), b.title, b.author, null, "")
            },
        )
        remoteBooks.forEach { book ->
            val (_, tracks) = apiClient.fetchBookDetail(book.id, token)
            catalogDao.upsertTracks(
                tracks.map { t ->
                    TrackEntity(t.id, t.bookId, t.sortOrder, t.title, t.filename, t.durationMs, t.localPath)
                },
            )
        }
        _uiState.update {
            it.copy(
                books = remoteBooks,
                downloadedBookIds = loadDownloadedBookIds(remoteBooks),
            )
        }
    }

    private suspend fun loadDownloadedBookIds(books: List<Book>): Set<String> = books
        .filter { book -> catalogDao.getTracksForBook(book.id).any { it.localPath != null } }
        .map { it.id }
        .toSet()

    private fun BookEntity.toDomain() = Book(
        id = id,
        slug = slug,
        contentType = if (contentType == "music") ContentType.MUSIC else ContentType.AUDIOBOOK,
        title = title,
        author = author,
    )

    private fun TrackEntity.toDomain() = Track(
        id = id,
        bookId = bookId,
        sortOrder = sortOrder,
        title = title,
        filename = filename,
        durationMs = durationMs,
        localPath = localPath,
    )

    private fun startBookPlayback(
        book: Book,
        downloadedTracks: List<Track>,
        startTrack: Track,
        startMs: Long,
    ) {
        val startIndex = downloadedTracks.indexOfFirst { it.id == startTrack.id }.coerceAtLeast(0)
        val queueItems = downloadedTracks.map { track ->
            QueuePlayItem(
                trackId = track.id,
                localPath = track.localPath!!,
                metadata = buildPlaybackMetadata(track, book, downloadedTracks),
            )
        }
        currentTrack = startTrack
        playbackClient.playQueue(queueItems, startIndex, startMs)
        _uiState.update {
            it.copy(nowPlayingTitle = startTrack.title, progressLabel = null)
        }
    }

    private fun buildPlaybackMetadata(track: Track, book: Book, downloadedTracks: List<Track>): PlaybackMetadata {
        val trackNumber = (downloadedTracks.indexOfFirst { it.id == track.id } + 1).coerceAtLeast(1)
        return PlaybackMetadata(
            trackTitle = track.title,
            artist = book.author ?: book.title,
            albumTitle = book.title,
            trackNumber = trackNumber,
            totalTracks = downloadedTracks.size,
            contentType = book.contentType,
        )
    }

    private fun onPlaybackEnded() {
        val book = _uiState.value.selectedBook ?: return
        val track = currentTrack ?: return
        if (book.contentType == ContentType.AUDIOBOOK) {
            saveAudiobookProgress(book.id, track.id, playbackClient.currentPositionMs())
        }
    }

    private fun maybeSaveProgress(positionMs: Long) {
        val book = _uiState.value.selectedBook ?: return
        if (book.contentType != ContentType.AUDIOBOOK) return
        val track = currentTrack ?: return
        val now = System.currentTimeMillis()
        if (now - lastProgressSaveMs < 15_000) return
        lastProgressSaveMs = now
        saveAudiobookProgress(book.id, track.id, positionMs)
    }
}
