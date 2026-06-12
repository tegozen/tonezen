package com.tonezen.app.ui.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.library.LibraryContentFilter
import com.tonezen.app.domain.library.LibraryFilterState
import com.tonezen.app.domain.library.LibrarySortOrder
import com.tonezen.app.domain.library.filterAndSortBooks
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

    private fun refreshSessionState(session: StoredSession?) {
        _uiState.update {
            it.copy(sessionState = sessionRepository.resolveState(session))
        }
    }

    private suspend fun loadLibrary(session: StoredSession?) {
        val refreshed = sessionRepository.refreshIfNeeded(session)
        refreshSessionState(refreshed)
        val local = catalogRepository.getAllBooks()
        val favorites = catalogRepository.getFavoriteBookIds()
        if (local.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    books = local,
                    downloadedBookIds = catalogRepository.downloadedBookIds(local),
                    favoriteBookIds = favorites,
                )
            }
        }
        if (networkMonitor.isOnline()) {
            refreshed?.let { progressSyncRepository.start(it) }
            syncCatalog(refreshed?.accessToken)
        }
    }

    private suspend fun syncCatalog(accessToken: String?) {
        val remoteBooks = catalogRepository.syncFromRemote(accessToken)
        _uiState.update {
            it.copy(
                books = remoteBooks,
                downloadedBookIds = catalogRepository.downloadedBookIds(remoteBooks),
                favoriteBookIds = catalogRepository.getFavoriteBookIds(),
            )
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                loadLibrary(sessionRepository.loadSession())
            } finally {
                _uiState.update { it.copy(isRefreshing = false) }
            }
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
}
