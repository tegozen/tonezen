package com.tonezen.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.Book
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.playback.PlaybackClient
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class LibraryViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val sessionRepository: SessionRepository,
    private val progressSyncRepository: ProgressSyncRepository,
    private val networkMonitor: NetworkMonitor,
    private val playbackClient: PlaybackClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.session.collectLatest { session ->
                refreshSessionState(session)
                loadLibrary(session)
            }
        }
        viewModelScope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
                _uiState.update {
                    it.copy(nowPlayingTitle = snapshot.trackTitle ?: it.nowPlayingTitle)
                }
            }
        }
    }

    fun selectBook(book: Book) {
        _uiState.update { it.copy(selectedBook = book) }
    }

    fun clearSelection() {
        playbackClient.pause()
        viewModelScope.launch {
            val books = _uiState.value.books
            _uiState.update {
                it.copy(
                    selectedBook = null,
                    nowPlayingTitle = null,
                    downloadedBookIds = catalogRepository.downloadedBookIds(books),
                )
            }
        }
    }

    fun logout() {
        progressSyncRepository.stop()
        playbackClient.stopAndRelease()
        sessionRepository.clearSession()
    }

    private fun refreshSessionState(session: StoredSession?) {
        _uiState.update {
            it.copy(sessionState = sessionRepository.resolveState(session))
        }
    }

    private suspend fun loadLibrary(session: StoredSession?) {
        val refreshed = sessionRepository.refreshIfNeeded(session)
        refreshSessionState(refreshed)
        val local = catalogRepository.getAllBooks()
        if (local.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    books = local,
                    downloadedBookIds = catalogRepository.downloadedBookIds(local),
                )
            }
        }
        if (networkMonitor.isOnline() && refreshed != null) {
            progressSyncRepository.start(refreshed)
            syncCatalog(refreshed)
        }
    }

    private suspend fun syncCatalog(session: StoredSession) {
        val refreshed = sessionRepository.refreshIfNeeded(session) ?: return
        refreshed.accessToken.let { progressSyncRepository.updateAuth(it) }
        val remoteBooks = catalogRepository.syncFromRemote(refreshed.accessToken)
        _uiState.update {
            it.copy(
                books = remoteBooks,
                downloadedBookIds = catalogRepository.downloadedBookIds(remoteBooks),
            )
        }
    }
}
