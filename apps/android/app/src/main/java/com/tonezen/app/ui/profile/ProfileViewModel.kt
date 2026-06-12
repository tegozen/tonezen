package com.tonezen.app.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.ProgressSyncRepository
import com.tonezen.app.data.remote.SessionRepository
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
class ProfileViewModel @Inject constructor(
    private val sessionRepository: SessionRepository,
    private val progressSyncRepository: ProgressSyncRepository,
    private val catalogRepository: CatalogRepository,
    private val networkMonitor: NetworkMonitor,
    private val playbackClient: PlaybackClient,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            sessionRepository.session.collectLatest { session ->
                _uiState.update {
                    it.copy(
                        sessionState = sessionRepository.resolveState(session),
                        email = session?.userId,
                        userId = session?.userId,
                    )
                }
                refreshStats()
            }
        }
    }

    fun refreshStats() {
        viewModelScope.launch {
            val stats = catalogRepository.getStorageStats()
            val pending = catalogRepository.getPendingSyncCount()
            _uiState.update {
                it.copy(
                    storageUsedBytes = stats.usedBytes,
                    pendingSyncCount = pending,
                )
            }
        }
    }

    fun setOverflowMenuVisible(visible: Boolean) {
        _uiState.update { it.copy(showOverflowMenu = visible) }
    }

    fun setSignOutConfirmVisible(visible: Boolean) {
        _uiState.update { it.copy(showSignOutConfirm = visible, showOverflowMenu = false) }
    }

    fun setSyncDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showSyncDialog = visible) }
    }

    fun syncNow() {
        if (!networkMonitor.isOnline()) {
            _uiState.update { it.copy(showSyncDialog = true) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(syncing = true) }
            try {
                val session = sessionRepository.refreshIfNeeded(sessionRepository.loadSession())
                if (session != null) {
                    progressSyncRepository.pullAll(session.accessToken)
                    progressSyncRepository.flushPending(session.accessToken)
                }
                refreshStats()
            } finally {
                _uiState.update { it.copy(syncing = false, lastSyncLabel = "today") }
            }
        }
    }

    fun logout() {
        progressSyncRepository.stop()
        playbackClient.stopAndRelease()
        sessionRepository.clearSession()
    }
}
