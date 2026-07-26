package com.tonezen.app.ui.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.tonezen.app.data.local.CatalogRepository
import com.tonezen.app.data.local.EnsureTrackOutcome
import com.tonezen.app.data.local.LocalLibraryNotifier
import com.tonezen.app.data.local.TrackDownloadEnsurer
import com.tonezen.app.data.network.NetworkMonitor
import com.tonezen.app.data.remote.CatalogSyncRepository
import com.tonezen.app.data.remote.DownloadRepository
import com.tonezen.app.data.remote.SessionRepository
import com.tonezen.app.domain.model.StoredSession
import com.tonezen.app.playback.DownloadQueueNotifier
import com.tonezen.app.playback.forMusic
import com.tonezen.app.playback.MusicPlaybackQueue
import com.tonezen.app.playback.PlaybackClient
import com.tonezen.app.playback.PlaybackEvents
import com.tonezen.app.playback.PlaybackQueueBuilder
import com.tonezen.app.playback.TrackDownloadQueueController
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class MusicViewModel @Inject constructor(
    private val catalogRepository: CatalogRepository,
    private val catalogSyncRepository: CatalogSyncRepository,
    private val sessionRepository: SessionRepository,
    private val downloadRepository: DownloadRepository,
    private val networkMonitor: NetworkMonitor,
    private val playbackClient: PlaybackClient,
    private val playbackQueueBuilder: PlaybackQueueBuilder,
    private val trackDownloadEnsurer: TrackDownloadEnsurer,
    private val downloadQueueController: TrackDownloadQueueController,
    private val downloadQueueNotifier: DownloadQueueNotifier,
    private val localLibraryNotifier: LocalLibraryNotifier,
    private val playbackEvents: PlaybackEvents,
    private val musicPlaybackQueue: MusicPlaybackQueue,
) : ViewModel() {
    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    private val session = MusicPlaybackSession()
    private val musicHandler = MusicHandler(
        uiState = _uiState,
        scope = viewModelScope,
        session = session,
        catalogRepository = catalogRepository,
        downloadRepository = downloadRepository,
        trackDownloadEnsurer = trackDownloadEnsurer,
        downloadQueueController = downloadQueueController,
        downloadQueueNotifier = downloadQueueNotifier,
        localLibraryNotifier = localLibraryNotifier,
        playbackClient = playbackClient,
        playbackQueueBuilder = playbackQueueBuilder,
        musicPlaybackQueue = musicPlaybackQueue,
        playbackErrorMessage = ::playbackErrorMessage,
    )
    private var catalogOwnerKey: String? = null
    private var pendingCatalogReload = false

    init {
        musicHandler.onBulkDownloadFinished = {
            viewModelScope.launch {
                if (pendingCatalogReload) {
                    pendingCatalogReload = false
                    reloadMusicCatalog()
                }
                refreshDownloads()
            }
        }
        viewModelScope.launch {
            var wasQueueActive = downloadQueueNotifier.snapshot().isActive
            var wasBulkDownloading = downloadQueueNotifier.snapshot().forMusic().isBulkDownloading
            downloadQueueNotifier.state.collect { state ->
                val musicQueue = state.forMusic()
                if (!state.isActive && pendingCatalogReload) {
                    pendingCatalogReload = false
                    reloadMusicCatalog()
                }
                if (wasQueueActive && !state.isActive) {
                    refreshDownloads()
                }
                if (wasBulkDownloading && !musicQueue.isBulkDownloading) {
                    musicHandler.onBulkDownloadFinished()
                }
                wasQueueActive = state.isActive
                wasBulkDownloading = musicQueue.isBulkDownloading
            }
        }
        _uiState.update { it.copy(isNetworkOnline = networkMonitor.isOnline()) }
        viewModelScope.launch {
            networkMonitor.online.collect { online ->
                val wasOnline = _uiState.value.isNetworkOnline
                _uiState.update { it.copy(isNetworkOnline = online) }
                if (wasOnline && !online) {
                    musicHandler.onNetworkOffline()
                }
            }
        }
        viewModelScope.launch {
            localLibraryNotifier.changes
                .debounce(LOCAL_LIBRARY_REFRESH_DEBOUNCE_MS)
                .collect {
                    refreshDownloads()
                }
        }
        viewModelScope.launch {
            sessionRepository.session.collectLatest { sessionData ->
                val ownerKey = sessionData?.userId ?: "__anonymous__"
                if (ownerKey != catalogOwnerKey) {
                    catalogOwnerKey = ownerKey
                    loadMusicLibrary(sessionData)
                }
            }
        }
        viewModelScope.launch {
            playbackClient.snapshot.collectLatest { snapshot ->
                val trackId = snapshot.trackId
                val isMusic = musicHandler.isMusicSnapshot(snapshot)
                if (isMusic && trackId != null) {
                    musicHandler.onMusicSnapshot(snapshot)
                }
                val musicPlayback = musicHandler.musicPlaybackUi(snapshot)
                val nowPlayingTitle = if (isMusic) {
                    snapshot.trackTitle ?: _uiState.value.nowPlayingTitle
                } else {
                    _uiState.value.nowPlayingTitle
                }
                _uiState.update {
                    it.copy(
                        musicPlayback = musicPlayback,
                        nowPlayingTitle = nowPlayingTitle,
                    )
                }
            }
        }
        viewModelScope.launch {
            playbackEvents.trackEnded.collect {
                musicHandler.handleMusicTrackEnded()
            }
        }
        viewModelScope.launch {
            catalogSyncRepository.catalogUpdated.collect {
                if (downloadQueueNotifier.state.value.isActive) {
                    pendingCatalogReload = true
                } else {
                    reloadMusicCatalog()
                }
            }
        }
    }

    fun onMusicTabSelected() = musicHandler.onMusicTabSelected()

    fun onMiniPlayerPlayPause() = musicHandler.onMiniPlayerPlayPause()

    fun playMusicWave() = musicHandler.playMusicWave()

    fun onMusicTrackClick(track: MusicListTrack) = musicHandler.onMusicTrackClick(track)

    fun downloadMusicTrack(track: MusicListTrack) = musicHandler.downloadMusicTrack(track)

    fun deleteMusicTrack(track: MusicListTrack) = musicHandler.deleteMusicTrack(track)

    fun downloadAllMusic() = musicHandler.downloadAllMusic()

    fun cancelAllDownloads() = musicHandler.cancelAllDownloads()

    fun clearMusicPlaybackError() {
        _uiState.update { it.copy(musicPlaybackErrorMessage = null) }
    }

    private fun refreshDownloads(reconcileLocalPaths: Boolean = true) {
        viewModelScope.launch {
            val downloadedTrackIds = musicHandler.resolveDownloadedTrackIdsForUi(reconcileLocalPaths)
            val trackList = musicHandler.refreshMusicTrackListWithDownloadedIds(downloadedTrackIds)
            _uiState.update { it.copy(musicTrackList = trackList) }
        }
    }

    private suspend fun loadMusicLibrary(sessionData: StoredSession?) {
        if (sessionData == null) {
            session.musicCandidates = emptyList()
            session.musicBookIdByTrackId = emptyMap()
            _uiState.update {
                it.copy(isLoadingCatalog = false, musicTrackList = emptyList(), hasMusicBooks = false)
            }
            return
        }
        reloadMusicCatalog()
        if (!networkMonitor.isOnline()) {
            _uiState.update { it.copy(isLoadingCatalog = false) }
            return
        }
        _uiState.update { it.copy(isLoadingCatalog = true) }
        try {
            val refreshed = withContext(Dispatchers.IO) { sessionRepository.refreshIfNeeded(sessionData) }
            withContext(Dispatchers.IO) { catalogRepository.syncFromRemote(refreshed?.accessToken) }
            reloadMusicCatalog()
        } catch (_: Exception) {
            // Best-effort remote refresh; local cache (already shown) remains authoritative.
        } finally {
            _uiState.update { it.copy(isLoadingCatalog = false) }
        }
    }

    private suspend fun reloadMusicCatalog() {
        musicHandler.reloadMusicCatalogData()
        val trackList = musicHandler.buildMusicTrackListForCatalogUpdate(rebuildMusic = true)
        _uiState.update {
            it.copy(
                musicTrackList = trackList,
                hasMusicBooks = session.musicCandidates.isNotEmpty(),
            )
        }
    }

    private fun playbackErrorMessage(failure: EnsureTrackOutcome.Failure?): String = when (failure) {
        EnsureTrackOutcome.Failure.OFFLINE -> "Нет сети — нужен интернет для первой загрузки"
        EnsureTrackOutcome.Failure.NO_SESSION -> "Войдите в аккаунт, чтобы скачать трек"
        EnsureTrackOutcome.Failure.DOWNLOAD_FAILED, null -> "Не удалось скачать трек"
    }

    private companion object {
        const val LOCAL_LIBRARY_REFRESH_DEBOUNCE_MS = 300L
    }
}
