package com.tonezen.app.ui.music

import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.StoredSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class MusicCatalogLoader(
    private val uiState: MutableStateFlow<MusicUiState>,
    private val scope: CoroutineScope,
    private val session: MusicPlaybackSession,
    private val catalogRepository: CatalogRepository,
    private val sessionRepository: SessionRepository,
    private val networkMonitor: NetworkMonitor,
    private val musicHandler: MusicHandler,
) {
    fun refreshDownloads(reconcileLocalPaths: Boolean = true) {
        scope.launch {
            val downloadedTrackIds = musicHandler.resolveDownloadedTrackIdsForUi(reconcileLocalPaths)
            val trackList = musicHandler.refreshMusicTrackListWithDownloadedIds(downloadedTrackIds)
            uiState.update { it.copy(musicTrackList = trackList) }
        }
    }

    suspend fun loadMusicLibrary(sessionData: StoredSession?) {
        if (sessionData == null) {
            session.musicCandidates = emptyList()
            session.musicBookIdByTrackId = emptyMap()
            uiState.update {
                it.copy(isLoadingCatalog = false, musicTrackList = emptyList(), hasMusicBooks = false)
            }
            return
        }
        reloadMusicCatalog()
        if (!networkMonitor.isOnline()) {
            uiState.update { it.copy(isLoadingCatalog = false) }
            return
        }
        uiState.update { it.copy(isLoadingCatalog = true) }
        try {
            val refreshed = withContext(Dispatchers.IO) { sessionRepository.refreshIfNeeded(sessionData) }
            withContext(Dispatchers.IO) { catalogRepository.syncFromRemote(refreshed?.accessToken) }
            reloadMusicCatalog()
        } catch (_: Exception) {
            // Best-effort remote refresh; local cache (already shown) remains authoritative.
        } finally {
            uiState.update { it.copy(isLoadingCatalog = false) }
        }
    }

    suspend fun reloadMusicCatalog() {
        musicHandler.reloadMusicCatalogData()
        val trackList = musicHandler.buildMusicTrackListForCatalogUpdate(rebuildMusic = true)
        uiState.update {
            it.copy(
                musicTrackList = trackList,
                hasMusicBooks = session.musicCandidates.isNotEmpty(),
            )
        }
    }
}
