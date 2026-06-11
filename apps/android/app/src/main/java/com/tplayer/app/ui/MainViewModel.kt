package com.tplayer.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tplayer.app.data.local.AudiobookProgressEntity
import com.tplayer.app.data.local.BookEntity
import com.tplayer.app.data.local.CatalogDao
import com.tplayer.app.data.local.FavoriteEntity
import com.tplayer.app.data.local.SecureSessionStore
import com.tplayer.app.data.local.TrackEntity
import com.tplayer.app.data.remote.ApiClient
import com.tplayer.app.data.remote.isNetworkAvailable
import com.tplayer.app.domain.model.AudiobookProgress
import com.tplayer.app.domain.model.Book
import com.tplayer.app.domain.model.ContentType
import com.tplayer.app.domain.model.SessionState
import com.tplayer.app.domain.model.StoredSession
import com.tplayer.app.domain.model.Track
import com.tplayer.app.domain.session.ProgressMerge
import com.tplayer.app.domain.session.SessionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class MainUiState(
    val sessionState: SessionState = SessionState.UNAUTHENTICATED,
    val books: List<Book> = emptyList(),
    val selectedBook: Book? = null,
    val tracks: List<Track> = emptyList(),
    val progressLabel: String? = null,
    val error: String? = null,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val catalogDao: CatalogDao,
    private val sessionStore: SecureSessionStore,
    private val sessionManager: SessionManager,
    private val apiClient: ApiClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    private var session: StoredSession? = null

    init {
        session = sessionStore.load()
        refreshSessionState()
        loadLibrary()
    }

    fun login(email: String, password: String) {
        viewModelScope.launch {
            try {
                // Demo session — replace with Supabase Auth in production deployment
                val demo = StoredSession(
                    userId = "demo-user",
                    accessToken = "demo-access-token",
                    refreshToken = "demo-refresh-token",
                    expiresAtEpochSeconds = System.currentTimeMillis() / 1000 + 3600,
                )
                sessionStore.save(demo)
                session = demo
                refreshSessionState()
                syncCatalog()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun logout() {
        sessionStore.clear()
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
        _uiState.update { it.copy(selectedBook = null, tracks = emptyList(), progressLabel = null) }
    }

    fun playBook() {
        // Playback started via PlaybackService intent from UI layer extension
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
            catalogDao.upsertProgress(entity)
            maybeSyncProgress(entity)
        }
    }

    private fun refreshSessionState() {
        val online = context.isNetworkAvailable()
        _uiState.update {
            it.copy(sessionState = sessionManager.resolveState(session, online))
        }
    }

    private fun loadLibrary() {
        viewModelScope.launch {
            val local = catalogDao.getAllBooks().map { it.toDomain() }
            if (local.isNotEmpty()) {
                _uiState.update { it.copy(books = local) }
            }
            if (sessionManager.canUseForApi(session, context.isNetworkAvailable())) {
                syncCatalog()
            }
        }
    }

    private suspend fun syncCatalog() {
        val token = session?.accessToken
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
        _uiState.update { it.copy(books = remoteBooks) }
    }

    private suspend fun maybeSyncProgress(entity: AudiobookProgressEntity) {
        val online = context.isNetworkAvailable()
        if (!sessionManager.shouldRefresh(session, online) && session != null && online) {
            val progress = AudiobookProgress(
                entity.bookId,
                entity.trackId,
                entity.positionMs,
                entity.updatedAtEpochMs,
            )
            session?.accessToken?.let { apiClient.pushProgress(it, entity.bookId, progress) }
        }
    }

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
}
