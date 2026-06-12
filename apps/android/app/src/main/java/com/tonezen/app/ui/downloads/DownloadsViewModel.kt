package com.tonezen.app.ui.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.remote.DownloadRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DownloadsUiState())
    val uiState: StateFlow<DownloadsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val summaries = catalogRepository.getDownloadedBookSummaries()
            val stats = catalogRepository.getStorageStats()
            _uiState.update { it.copy(summaries = summaries, storageStats = stats) }
        }
    }

    fun selectTab(tab: Int) {
        _uiState.update { it.copy(selectedTab = tab) }
    }

    fun showDeleteAllConfirm(show: Boolean) {
        _uiState.update { it.copy(showDeleteAllConfirm = show) }
    }

    fun deleteAll() {
        viewModelScope.launch {
            try {
                catalogRepository.deleteAllDownloads()
                refresh()
                _uiState.update { it.copy(showDeleteAllConfirm = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message, showDeleteAllConfirm = false) }
            }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            catalogRepository.clearLocalDownloads(bookId)
            catalogRepository.getTracksForBook(bookId).forEach { track ->
                downloadRepository.deleteLocalTrack(bookId, track.id)
            }
            refresh()
        }
    }
}
